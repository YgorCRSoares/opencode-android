#!/usr/bin/env bash
# Baixa, valida (SHA256) e reempacota o runtime pinado do OpenCode (arm64) + ripgrep
# + libpcre2-8 (dependência do rg ausente no Termux .deb).
# Produz: app/src/main/assets/runtime-arm64.zip (5 entradas, nunca commitado)
# e app/src/main/jniLibs/arm64-v8a/ (binários executáveis, nunca commitados).
# shellcheck disable=SC2016  # '$ORIGIN' é literal (RUNPATH do ELF), não expansão shell.
set -euo pipefail

# ----- Pins (não redescobrir) -----
# Pins dinâmicos (última release incluindo prereleases ordenada por data)
# Override por env: RUNTIME_URL / RUNTIME_SHA256
_latest_json() {
    curl -fsSL "https://api.github.com/repos/guysoft/opencode-termux/releases?per_page=30" | \
        jq -r '[.[] | select(.assets[] | .name | test("opencode2-.*-android-aarch64\\.zip$"))] | sort_by(.published_at) | reverse | .[0]'
}
_rel_data=$(_latest_json)
_rel_tag=$(echo "$_rel_data" | jq -r '.tag_name')
_rel_url=$(echo "$_rel_data" | jq -r '.assets[] | select(.name | test("opencode2-.*-android-aarch64\\.zip$")) | .browser_download_url')
_rel_zip=$(echo "$_rel_data" | jq -r '.assets[] | select(.name | test("opencode2-.*-android-aarch64\\.zip$")) | .name')
_sums_url=$(echo "$_rel_data" | jq -r '.assets[] | select(.name == "SHA256SUMS") | .browser_download_url')
_rel_sha=$(curl -fsSL "$_sums_url" | grep "$_rel_zip" | awk '{print $1}')

readonly RUNTIME_URL="${RUNTIME_URL:-$_rel_url}"
readonly RUNTIME_SHA256="${RUNTIME_SHA256:-$_rel_sha}"
readonly RUNTIME_TAG="${_rel_tag}" # tag da última release detectada
readonly RIPGREP_URL="https://packages.termux.dev/apt/termux-main/pool/main/r/ripgrep/ripgrep_15.2.0_aarch64.deb"
readonly RIPGREP_SHA256="38e28bc297000517b24702568a483eca7dc3323eb6bdccc9033f031776bdcc6c"
# rg é linkado contra libpcre2-8.so (RUNPATH original aponta para /data/data/com.termux,
# inexistente no app): empacotamos a lib e reescrevemos o RUNPATH para $ORIGIN.
readonly PCRE2_URL="https://packages.termux.dev/apt/termux-main/pool/main/p/pcre2/pcre2_10.49_aarch64.deb"
readonly PCRE2_SHA256="c27995e5f52b8ecc9b8b3e662eb08e49f82a18bfd3f34343936acc7b23964724"

readonly CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/opencode-android-build"
readonly RUNTIME_CACHE="$CACHE_DIR/$_rel_zip" # nome acompanha a release detectada
readonly RIPGREP_CACHE="$CACHE_DIR/ripgrep_15.2.0_aarch64.deb"
readonly PCRE2_CACHE="$CACHE_DIR/pcre2_10.49_aarch64.deb"
# Stamp: registra o SHA do zip CRU da release que já foi re-empacotado em
# OUTPUT_ZIP. Como o zip embarcado é re-empacotado (5 entradas), seu SHA nunca
# é igual ao da release — comparamos via este stamp.
readonly RUNTIME_STAMP="$CACHE_DIR/runtime-embedded.sha"

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly PROJECT_ROOT
OUTPUT_ZIP="$PROJECT_ROOT/app/src/main/assets/runtime-arm64.zip"
readonly OUTPUT_ZIP
JNI_DIR="$PROJECT_ROOT/app/src/main/jniLibs/arm64-v8a"
readonly JNI_DIR
MAIN_ACTIVITY="$PROJECT_ROOT/app/src/main/java/dev/opencode/terminal/MainActivity.java"
readonly MAIN_ACTIVITY

# ----- Modo --check: só informa, zero efeito colateral -----
# Compara a release detectada (via GitHub) com o stamp de qual release foi
# re-empacotada no zip embarcado. Não mexe em nada.
if [[ "${1:-}" == "--check" ]]; then
    _packed_sha=""
    [[ -f "$RUNTIME_STAMP" ]] && _packed_sha=$(cat "$RUNTIME_STAMP")
    printf '[fetch-runtime] Última release do fork: %s\n' "$RUNTIME_TAG" >&2
    printf '[fetch-runtime] SHA da release:         %s\n' "$RUNTIME_SHA256" >&2
    printf '[fetch-runtime] Release já embutida:    %s\n' "${_packed_sha:-<nenhuma>}" >&2
    if [[ -n "$_packed_sha" && "$_packed_sha" == "$RUNTIME_SHA256" ]]; then
        printf '[fetch-runtime] já está na última versão possível (%s); nada a fazer\n' "$RUNTIME_TAG" >&2
    else
        printf '[fetch-runtime] há atualização disponível (%s) — rode sem --check para aplicar\n' "$RUNTIME_TAG" >&2
    fi
    exit 0
fi

STAGING_DIR="$(mktemp -d)"
readonly STAGING_DIR
trap 'rm -rf "$STAGING_DIR"' EXIT

log() { printf '[fetch-runtime] %s\n' "$*" >&2; }

verify_sha256() {
    local file_path="$1"
    local expected="$2"
    local checksum_file
    checksum_file="$(mktemp)"
    printf '%s  %s\n' "$expected" "$file_path" >"$checksum_file"
    local rc=0
    sha256sum -c "$checksum_file" >/dev/null 2>&1 || rc=$?
    rm -f "$checksum_file"
    return "$rc"
}

download_pinned() {
    local url="$1"
    local expected_sha="$2"
    local dest="$3"

    if [[ -f "$dest" ]] && verify_sha256 "$dest" "$expected_sha"; then
        log "cache hit (sha256 ok): $dest"
        return 0
    fi
    rm -f "$dest" "$dest.tmp"

    log "downloading: $url"
    curl -fL --retry 3 --show-error -o "$dest.tmp" "$url"
    if ! verify_sha256 "$dest.tmp" "$expected_sha"; then
        rm -f "$dest.tmp"
        log "SHA256 mismatch for $url" >&2
        exit 1
    fi
    mv -f "$dest.tmp" "$dest"
    log "downloaded + verified: $dest"
}

assert_aarch64_elf() {
    local file_path="$1"
    if ! LC_ALL=C readelf -h "$file_path" >/dev/null 2>&1; then
        log "not an ELF file (readelf failed): $file_path" >&2
        exit 1
    fi
    local machine
    machine="$(LC_ALL=C readelf -h "$file_path" | sed -n 's/^[[:space:]]*Machine:[[:space:]]*//p')"
    if [[ "$machine" != "AArch64" ]]; then
        log "expected AArch64, got '$machine': $file_path" >&2
        exit 1
    fi
}

# ----- Ferramentas obrigatórias -----
command -v bsdtar >/dev/null 2>&1 || { log "bsdtar is required"; exit 1; }
command -v curl >/dev/null 2>&1 || { log "curl is required"; exit 1; }
command -v sha256sum >/dev/null 2>&1 || { log "sha256sum is required"; exit 1; }
command -v readelf >/dev/null 2>&1 || { log "readelf is required"; exit 1; }
command -v patchelf >/dev/null 2>&1 || { log "patchelf is required (rg RUNPATH rewrite)"; exit 1; }

mkdir -p "$CACHE_DIR"
# No-op: se o stamp indica que esta release EXATA já foi re-empacotada no zip
# embarcado, não há nada a fazer (evita re-download/repack/bump a cada execução).
if [[ -f "$RUNTIME_STAMP" && "$(cat "$RUNTIME_STAMP")" == "$RUNTIME_SHA256" ]]; then
    log "runtime já está na última versão ($RUNTIME_TAG); nada a fazer"
    exit 0
fi

download_pinned "$RUNTIME_URL" "$RUNTIME_SHA256" "$RUNTIME_CACHE"
download_pinned "$RIPGREP_URL" "$RIPGREP_SHA256" "$RIPGREP_CACHE"
download_pinned "$PCRE2_URL" "$PCRE2_SHA256" "$PCRE2_CACHE"

# ----- Extrai runtime (3 arquivos flat) -----
RUNTIME_DIR="$STAGING_DIR/runtime"
mkdir -p "$RUNTIME_DIR"
bsdtar -xf "$RUNTIME_CACHE" -C "$RUNTIME_DIR"
for runtime_file in opencode2 opencode2.bin libopentui.so; do
    if [[ ! -f "$RUNTIME_DIR/$runtime_file" ]]; then
        log "missing runtime file: $runtime_file" >&2
        exit 1
    fi
done

assert_aarch64_elf "$RUNTIME_DIR/opencode2.bin"

# ----- Extrai deb (inner data.tar.xz), localiza rg por sufixo /bin/rg -----
DEB_DIR="$STAGING_DIR/deb"
mkdir -p "$DEB_DIR"
bsdtar -xf "$RIPGREP_CACHE" -C "$DEB_DIR" data.tar.xz
bsdtar -xf "$DEB_DIR/data.tar.xz" -C "$DEB_DIR"

RG_PATH=""
while IFS= read -r -d '' candidate; do
    if [[ -z "$RG_PATH" ]]; then
        RG_PATH="$candidate"
    else
        log "multiple rg candidates found" >&2
        exit 1
    fi
done < <(find "$DEB_DIR" -type f -path '*/bin/rg' -print0)

if [[ -z "$RG_PATH" ]]; then
    log "rg not found inside deb" >&2
    exit 1
fi
log "found rg: ${RG_PATH#"$DEB_DIR"/}"

assert_aarch64_elf "$RG_PATH"
if LC_ALL=C readelf -d "$RG_PATH" | grep -q 'libc\.so\.6'; then
    log "rg links glibc (libc.so.6) — expected bionic only" >&2
    exit 1
fi

# ----- Extrai libpcre2-8.so do deb do Termux (dependência do rg) -----
PCRE2_DIR="$STAGING_DIR/pcre2"
mkdir -p "$PCRE2_DIR"
bsdtar -xf "$PCRE2_CACHE" -C "$PCRE2_DIR" data.tar.xz
bsdtar -xf "$PCRE2_DIR/data.tar.xz" -C "$PCRE2_DIR"

PCRE2_PATH=""
while IFS= read -r -d '' candidate; do
    if [[ -z "$PCRE2_PATH" ]]; then
        PCRE2_PATH="$candidate"
    else
        log "multiple libpcre2-8 candidates found" >&2
        exit 1
    fi
done < <(find "$PCRE2_DIR" -type f -path '*/lib/libpcre2-8.so' -print0)

if [[ -z "$PCRE2_PATH" ]]; then
    log "libpcre2-8.so not found inside deb" >&2
    exit 1
fi
log "found libpcre2-8.so: ${PCRE2_PATH#"$PCRE2_DIR"/}"

assert_aarch64_elf "$PCRE2_PATH"
if LC_ALL=C readelf -d "$PCRE2_PATH" | grep -q 'libc\.so\.6'; then
    log "libpcre2-8.so links glibc (libc.so.6) — expected bionic only" >&2
    exit 1
fi

# ----- Monta staging com layout pinado -----
PKG_DIR="$STAGING_DIR/pkg"
mkdir -p "$PKG_DIR/opencode" "$PKG_DIR/bin"
install -m 0755 "$RUNTIME_DIR/opencode2" "$PKG_DIR/opencode/opencode2"
install -m 0755 "$RUNTIME_DIR/opencode2.bin" "$PKG_DIR/opencode/opencode2.bin"
install -m 0755 "$RUNTIME_DIR/libopentui.so" "$PKG_DIR/opencode/libopentui.so"
install -m 0755 "$RG_PATH" "$PKG_DIR/bin/rg"
install -m 0644 "$PCRE2_PATH" "$PKG_DIR/bin/libpcre2-8.so"

# RUNPATH original aponta para /data/data/com.termux/files/usr/lib (inexistente
# no app). Reescreve para $ORIGIN: rg acha a lib no próprio diretório sem
# depender de LD_LIBRARY_PATH (que o bionic pode restringir por namespace).
patchelf --set-rpath '$ORIGIN' "$PKG_DIR/bin/rg"
rg_runpath="$(LC_ALL=C readelf -d "$PKG_DIR/bin/rg" | sed -n 's/.*(RUNPATH).*\[\(.*\)\]/\1/p')"
if [[ "$rg_runpath" != '$ORIGIN' ]]; then
    log "unexpected rg RUNPATH after patchelf: '$rg_runpath'" >&2
    exit 1
fi

# ----- Layout de execução no APK (Android 10+ W^X) -----
# filesDir é gravável e untrusted_app não pode executar de lá. O diretório
# nativo extraído do APK tem contexto/permissão de execução; os nomes viram
# lib*.so para o PackageManager extrair em /data/app/.../lib/arm64/.
mkdir -p "$JNI_DIR"
install -m 0755 "$PKG_DIR/opencode/opencode2.bin" "$JNI_DIR/libopencode2.so"
install -m 0755 "$PKG_DIR/opencode/libopentui.so" "$JNI_DIR/libopentui.so"
install -m 0755 "$PKG_DIR/bin/rg" "$JNI_DIR/librg.so"
install -m 0755 "$PKG_DIR/bin/libpcre2-8.so" "$JNI_DIR/libpcre2-8.so"

for native_name in libopencode2.so libopentui.so librg.so libpcre2-8.so; do
    assert_aarch64_elf "$JNI_DIR/$native_name"
    if [[ ! -x "$JNI_DIR/$native_name" ]]; then
        log "native runtime not executable: $JNI_DIR/$native_name" >&2
        exit 1
    fi
done
if [[ "$(LC_ALL=C readelf -d "$JNI_DIR/librg.so" | sed -n 's/.*(RUNPATH).*\[\(.*\)\]/\1/p')" != '$ORIGIN' ]]; then
    log "native librg.so RUNPATH is not \$ORIGIN" >&2
    exit 1
fi
log "wrote native runtime: $JNI_DIR"

create_zip() {
    local src_dir="$1"
    local out_file="$2"
    if command -v zip >/dev/null 2>&1; then
        (cd "$src_dir" && zip -q -r "$out_file" \
            opencode/opencode2 opencode/opencode2.bin opencode/libopentui.so \
            bin/rg bin/libpcre2-8.so)
    elif command -v bsdtar >/dev/null 2>&1; then
        bsdtar --format=zip -cf "$out_file" -C "$src_dir" \
            opencode/opencode2 opencode/opencode2.bin opencode/libopentui.so \
            bin/rg bin/libpcre2-8.so
    else
        python3 - "$src_dir" "$out_file" <<'PY'
import os
import sys
import zipfile

src_dir, out_file = sys.argv[1], sys.argv[2]
entries = [
    "opencode/opencode2",
    "opencode/opencode2.bin",
    "opencode/libopentui.so",
    "bin/rg",
    "bin/libpcre2-8.so",
]
with zipfile.ZipFile(out_file, "w", zipfile.ZIP_DEFLATED) as zf:
    for rel in entries:
        zf.write(os.path.join(src_dir, rel), rel)
PY
    fi
}

mkdir -p "$(dirname "$OUTPUT_ZIP")"
rm -f "$OUTPUT_ZIP"
create_zip "$PKG_DIR" "$OUTPUT_ZIP"
log "wrote $OUTPUT_ZIP"

# ----- Asserts internos -----
mapfile -t entry_list < <(bsdtar -tf "$OUTPUT_ZIP" | sed '/^$/d')

if [[ ${#entry_list[@]} -ne 5 ]]; then
    log "expected 5 entries, got ${#entry_list[@]}" >&2
    bsdtar -tf "$OUTPUT_ZIP" >&2
    exit 1
fi
for entry in "${entry_list[@]}"; do
    if [[ "$entry" == /* || "$entry" == ./* ]]; then
        log "entry not anchored at root: $entry" >&2
        exit 1
    fi
done

readonly -a expected_entries=(
    "opencode/opencode2"
    "opencode/opencode2.bin"
    "opencode/libopentui.so"
    "bin/rg"
    "bin/libpcre2-8.so"
)
expected_sorted="$(printf '%s\n' "${expected_entries[@]}" | sort)"
actual_sorted="$(printf '%s\n' "${entry_list[@]}" | sort)"
if [[ "$expected_sorted" != "$actual_sorted" ]]; then
    log "unexpected entry set" >&2
    bsdtar -tf "$OUTPUT_ZIP" >&2
    exit 1
fi

# Re-extração limpa do bin/rg + libpcre2 sidecar + checagens ELF/RUNPATH.
REEXTRACT_DIR="$STAGING_DIR/reextracted"
mkdir -p "$REEXTRACT_DIR"
bsdtar -xf "$OUTPUT_ZIP" -C "$REEXTRACT_DIR" bin/rg bin/libpcre2-8.so
assert_aarch64_elf "$REEXTRACT_DIR/bin/rg"
assert_aarch64_elf "$REEXTRACT_DIR/bin/libpcre2-8.so"
if LC_ALL=C readelf -d "$REEXTRACT_DIR/bin/rg" | grep -q 'libc\.so\.6'; then
    log "re-extracted rg links glibc (libc.so.6)" >&2
    exit 1
fi
if [[ "$(LC_ALL=C readelf -d "$REEXTRACT_DIR/bin/rg" | sed -n 's/.*(RUNPATH).*\[\(.*\)\]/\1/p')" != '$ORIGIN' ]]; then
    log "re-extracted rg RUNPATH is not \$ORIGIN" >&2
    exit 1
fi
if ! LC_ALL=C readelf -d "$REEXTRACT_DIR/bin/rg" | grep -q 'libpcre2-8\.so'; then
    log "re-extracted rg does not NEED libpcre2-8.so" >&2
    exit 1
fi

# Bump do RUNTIME_MARKER_VERSION só aqui: depois do novo runtime validado e
# re-empacotado. Gatilha re-extração do runtime no dispositivo na próxima
# instalação. Se o script falhar antes, o marker não vira (evita bump duplo).
if [[ -f "$MAIN_ACTIVITY" ]]; then
    log "Fazendo bump de RUNTIME_MARKER_VERSION em MainActivity.java"
    python3 -c '
import re
path = "'"$MAIN_ACTIVITY"'"
with open(path, "r") as f:
    content = f.read()
# Incrementa o número do marker (ex: 1.1 -> 1.2, ou 1.2 -> 1.3)
def bump(m):
    prefix = m.group(1)
    num = float(m.group(2))
    return prefix + "{0:.1f}".format(num + 0.1)
new_content = re.sub(r"(RUNTIME_MARKER_VERSION\s*=\s*\")([0-9.]+)", bump, content)
with open(path, "w") as f:
    f.write(new_content)
'
    log "RUNTIME_MARKER_VERSION agora: $(grep -o 'RUNTIME_MARKER_VERSION = "[0-9.]*"' "$MAIN_ACTIVITY")"
fi

# Registra no stamp que esta release exata foi embutida (habilita no-op futuro).
printf '%s\n' "$RUNTIME_SHA256" >"$RUNTIME_STAMP"
log "stamp gravado: $RUNTIME_STAMP"

log "OK: 5 zip entries + 4 native-lib executables, aarch64, bionic-only rg, pcre2 sidecar + RUNPATH \$ORIGIN"
log "bsdtar -tf:"
bsdtar -tf "$OUTPUT_ZIP" >&2