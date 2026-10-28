package dev.opencode.terminal;

/**
 * Aplica {@code TIOCSWINSZ} no PTY somente enquanto o master ainda está aberto.
 *
 * <p>{@link jackpal.androidterm.emulatorview.TermSession#finish()} fecha os
 * streams de entrada/saída que compartilham o {@code ParcelFileDescriptor} do
 * master. Um passe de layout pode chamar {@code updateSize()} depois disso;
 * nesse momento {@code ParcelFileDescriptor.getFd()} lança
 * {@link IllegalStateException} e derrubaria o app. Esta guarda transforma esse
 * caso em no-op e mantém a redimensionamento idempotente.</p>
 */
final class PtyWindowSizer {

    interface FdSource {
        int fd() throws IllegalStateException;
    }

    interface WindowSetter {
        void set(int fd, int rows, int columns);
    }

    private PtyWindowSizer() {
    }

    /**
     * @return {@code true} se o ioctl foi aplicado; {@code false} se o PTY já
     *         estava fechado ou fechou durante a chamada.
     */
    static boolean apply(FdSource source, boolean closed, int columns, int rows,
            WindowSetter setter) {
        if (closed || source == null) {
            return false;
        }
        try {
            setter.set(source.fd(), rows, columns);
            return true;
        } catch (IllegalStateException alreadyClosed) {
            return false;
        }
    }
}
