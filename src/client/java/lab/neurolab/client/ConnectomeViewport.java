package lab.neurolab.client;

/** Keeps the anatomical projection usable at Minecraft's smaller GUI-scaled resolutions. */
record ConnectomeViewport(int left, int top, int right, int bottom, boolean compact,
                           int densityButtonX, int filterButtonX, int buttonWidth, int buttonY) {
    static ConnectomeViewport calculate(int width, int height) {
        boolean compact = width < 650 || height < 320;
        if (compact) {
            int buttonWidth = Math.max(60, (width - 38) / 2);
            return new ConnectomeViewport(16, 55, Math.max(17, width - 16), Math.max(56, height - 40),
                    true, 16, width - 16 - buttonWidth, buttonWidth, 28);
        }
        return new ConnectomeViewport(24, 70, width - 252, height - 54,
                false, width - 460, width - 238, 208, 21);
    }

    boolean inDensityButton(double x, double y) {
        return x >= densityButtonX && x < densityButtonX + buttonWidth
                && y >= buttonY && y < buttonY + 20;
    }

    boolean inFilterButton(double x, double y) {
        return x >= filterButtonX && x < filterButtonX + buttonWidth
                && y >= buttonY && y < buttonY + 20;
    }
}
