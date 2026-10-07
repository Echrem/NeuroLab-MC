package lab.neurolab.client;

/** Bounded row geometry for the dashboard's scrollable live-subject list. */
record MobListWindow(int rowHeight, int visibleCount, int maxFirstIndex) {
    static MobListWindow calculate(int totalSubjects, int panelHeight) {
        int rowHeight = totalSubjects > 4 ? 18 : 22;
        int visibleCount = Math.max(1, (panelHeight - 22) / rowHeight);
        return new MobListWindow(rowHeight, visibleCount, Math.max(0, totalSubjects - visibleCount));
    }

    int clampFirstIndex(int firstIndex) {
        return Math.max(0, Math.min(firstIndex, maxFirstIndex));
    }

    int indexAt(double mouseY, int listStartY, int firstIndex) {
        int row = (int) ((mouseY - listStartY) / rowHeight);
        return row >= 0 && row < visibleCount ? firstIndex + row : -1;
    }
}
