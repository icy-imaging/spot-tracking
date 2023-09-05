package plugins.nchenouard.particletracking.MHTracker;

import Jama.Matrix;
import plugins.nchenouard.spot.Spot;

/**
 * Describes the association between a Spot and a putative track
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */

public class Association {
    Matrix state;
    Spot spot;
    boolean isPrediction;
    int t;

    private double bestScore;

    public Association(final Matrix state, final Spot spot, final boolean isPrediction, final int t, final double initScore) {
        this.state = state;
        this.spot = spot;
        this.isPrediction = isPrediction;
        this.t = t;
        this.bestScore = initScore;
    }

    public synchronized void setNewScore(final double score) {
        if (score > bestScore)
            bestScore = score;
    }

    public double getBestScore() {
        return bestScore;
    }
}
