package jackpal.androidterm.emulatorview;

/**
 * Helper imutável para normalizar o intervalo de seleção.
 * Garante que (startX, startY) seja sempre cronologicamente anterior a (endX, endY).
 */
public final class SelectionRange {
    public final int startX;
    public final int startY;
    public final int endX;
    public final int endY;

    private SelectionRange(int startX, int startY, int endX, int endY) {
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
    }

    /**
     * Cria um intervalo normalizado a partir de um ponto de ancoragem e do ponto atual de arrasto.
     * Se o ponto atual estiver "atrás" da âncora, os pontos são invertidos no resultado.
     */
    public static SelectionRange fromDrag(int anchorX, int anchorY,
                                          int currentX, int currentY) {
        if (anchorY < currentY || (anchorY == currentY && anchorX <= currentX)) {
            return new SelectionRange(anchorX, anchorY, currentX, currentY);
        }
        // Inverte para manter a ordem linear (de cima para baixo, da esquerda para a direita)
        return new SelectionRange(currentX, currentY, anchorX, anchorY);
    }
}
