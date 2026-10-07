package lab.neurolab.client;

/** Vertical layout for the overview and subject details at Minecraft GUI-scaled sizes. */
record NeuroAnalysisViewport(int panelTop, int panelBottom, int overviewTop, int overviewHeight,
                             int statusTop, int statusHeight, int chartTop, int chartBottom) {
    static int popupHeight(int screenHeight) {
        return Math.max(1, Math.min(700, screenHeight - 8));
    }

    static int popupTop(int screenHeight) {
        return Math.max(0, (screenHeight - popupHeight(screenHeight)) / 2);
    }

    static NeuroAnalysisViewport calculate(int screenHeight, int headerOffset, boolean compact, boolean detail) {
        int top = popupTop(screenHeight);
        int bottom = top + popupHeight(screenHeight);
        int overviewTop = top + headerOffset;
        // Let the anatomical preview grow on ordinary displays while reserving plot space.
        int baseOverview = compact ? 96 : 116;
        int statusHeight = detail && screenHeight >= 320 ? 64 : 32;
        int availableOverview = bottom - 27 - 8 - statusHeight - 8 - overviewTop
                - (compact ? 60 : 120);
        int overviewHeight = Math.max(baseOverview,
                Math.min(220, availableOverview));
        int statusTop = detail ? overviewTop : overviewTop + overviewHeight + 8;
        return new NeuroAnalysisViewport(top, bottom, overviewTop, overviewHeight, statusTop, statusHeight,
                statusTop + statusHeight + 8, bottom - 27);
    }
}
