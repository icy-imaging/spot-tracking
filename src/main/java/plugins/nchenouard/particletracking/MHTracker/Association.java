/*
 * Copyright (c) 2010-2024. Institut Pasteur.
 *
 * This file is part of Icy.
 * Icy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Icy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Icy. If not, see <https://www.gnu.org/licenses/>.
 */

package plugins.nchenouard.particletracking.MHTracker;

import Jama.Matrix;
import org.jetbrains.annotations.Contract;
import plugins.nchenouard.spot.Spot;

/**
 * Describes the association between a Spot and a putative track
 * <p>
 * Part of the Spot Tracking plugin for ICY: <a href="https://icy.bioimageanalysis.org/plugin/Spot_Tracking">https://icy.bioimageanalysis.org/plugin/Spot_Tracking</a>
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

    @Contract(pure = true)
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
