package lab.neurolab.client;

/** Non-overlapping toolbar geometry shared by the monitor and keyboard dashboard entry points. */
record NeuroAnalysisHeaderLayout(
        int panelLeft,
        int panelWidth,
        boolean compact,
        boolean hasAttachAction,
        int contentTopOffset,
        int titleWidth,
        int[] buttonX,
        int[] buttonY,
        int[] buttonWidth) {

    private static final int GAP = 4;
    private static final int[] NORMAL_WITH_ATTACH = {54, 64, 46, 80, 70, 54, 64};
    private static final int[] NORMAL_WITHOUT_ATTACH = {54, 64, 80, 70, 54, 64};
    private static final int[] COMPACT_WITH_ATTACH = {44, 52, 42, 72, 40, 50, 62};
    private static final int[] COMPACT_WITHOUT_ATTACH = {44, 52, 72, 40, 50, 62};

    static NeuroAnalysisHeaderLayout calculate(int screenWidth, int panelY, boolean hasAttachAction) {
        int panelWidth = Math.max(0, Math.min(1040, screenWidth - 48));
        int panelLeft = (screenWidth - panelWidth) / 2;
        int[] normalWidths = hasAttachAction ? NORMAL_WITH_ATTACH : NORMAL_WITHOUT_ATTACH;
        int normalTotal = total(normalWidths);
        boolean compact = panelWidth < normalTotal + 128;
        int[] widths = (compact
                ? hasAttachAction ? COMPACT_WITH_ATTACH : COMPACT_WITHOUT_ATTACH
                : normalWidths).clone();
        int[] xs = new int[widths.length];
        int[] ys = new int[widths.length];

        if (compact) {
            int controlsX = panelLeft + 18;
            int firstRowCount = hasAttachAction ? 4 : 3;
            for (int i = 0; i < widths.length; i++) {
                xs[i] = controlsX;
                ys[i] = panelY + (i < firstRowCount ? 28 : 50);
                controlsX += widths[i] + GAP;
            }
            int available = Math.max(0, panelWidth - 36);
            fitRow(widths, 0, firstRowCount, available);
            fitRow(widths, firstRowCount, widths.length - firstRowCount, available);
            for (int i = 1; i < firstRowCount; i++) xs[i] = xs[i - 1] + widths[i - 1] + GAP;
            if (firstRowCount < widths.length) xs[firstRowCount] = panelLeft + 18;
            for (int i = firstRowCount + 1; i < widths.length; i++)
                xs[i] = xs[i - 1] + widths[i - 1] + GAP;
            return new NeuroAnalysisHeaderLayout(panelLeft, panelWidth, true, hasAttachAction,
                    74, Math.max(0, panelWidth - 36), xs, ys, widths);
        }

        int controlsWidth = total(widths);
        int controlsX = panelLeft + panelWidth - 18 - controlsWidth;
        for (int i = 0; i < widths.length; i++) {
            xs[i] = controlsX;
            ys[i] = panelY + 8;
            controlsX += widths[i] + GAP;
        }
        int titleWidth = Math.max(0, xs[0] - (panelLeft + 18) - 8);
        return new NeuroAnalysisHeaderLayout(panelLeft, panelWidth, false, hasAttachAction,
                50, titleWidth, xs, ys, widths);
    }

    int buttonX(int index) { return buttonX[index]; }
    int buttonY(int index) { return buttonY[index]; }
    int buttonWidth(int index) { return buttonWidth[index]; }

    int connectomeIndex() { return hasAttachAction ? 3 : 2; }
    int guideIndex() { return hasAttachAction ? 4 : 3; }
    int freezeIndex() { return hasAttachAction ? 5 : 4; }
    int settingsIndex() { return hasAttachAction ? 6 : 5; }

    private static int total(int[] widths) {
        int sum = GAP * Math.max(0, widths.length - 1);
        for (int width : widths) sum += width;
        return sum;
    }

    private static void fitRow(int[] widths, int start, int count, int available) {
        int rowWidth = GAP * Math.max(0, count - 1);
        for (int i = start; i < start + count; i++) rowWidth += widths[i];
        if (rowWidth <= available || count == 0) return;
        double scale = Math.max(0, available - GAP * (count - 1))
                / (double) Math.max(1, rowWidth - GAP * (count - 1));
        int used = GAP * (count - 1);
        for (int i = start; i < start + count; i++) {
            widths[i] = (int) Math.floor(widths[i] * scale);
            used += widths[i];
        }
        int remainder = Math.max(0, available - used);
        for (int i = start; i < start + count && remainder > 0; i++, remainder--) widths[i]++;
    }
}
