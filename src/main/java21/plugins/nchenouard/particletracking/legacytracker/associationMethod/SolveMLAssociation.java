/*
 * Copyright (c) 2010-2026. Institut Pasteur.
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

package plugins.nchenouard.particletracking.legacytracker.associationMethod;

import org.jetbrains.annotations.NotNull;
import plugins.nchenouard.spot.Spot;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Solve the assignment problem by local ML maximization, as described in
 * Genovesio A, Liedl T, Emiliani V, Parak WJ, Coppey-Moisan M, Olivo-Marin JC.
 * Multiple particle tracking in 3-D+t microscopy: method and application to the
 * tracking of endocytosed quantum dots. IEEE Trans Image Process. 2006
 * May;15(5):1062-70. PubMed PMID: 16671288.
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com). Based on Auguste Genovesio original code.
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class SolveMLAssociation extends AssignProblemSolver {
    public final static int methoId = 0;
    protected final boolean addNewObjects;

    public SolveMLAssociation(final boolean addNewObjects) {
        this.addNewObjects = addNewObjects;
    }

    protected void solve1(final @NotNull AssignProblem pb) {
        final Track track = pb.tracks.getFirst();
        final Spot spot = pb.detections.getFirst();
        track.associate(spot, pb.t);
    }

    protected void solve2(final AssignProblem pb) {
        if (addNewObjects)
            pb.tracksToBeCreated.add(pb.detections.getFirst());
    }

    protected void solve3(final @NotNull AssignProblem pb) {
        pb.tracksToBeProlongated.add(pb.tracks.getFirst());
    }

    protected void solve4(final AssignProblem pb, final int num) {
        asignOpt(pb, num, num);
    }

    protected void solve5(final AssignProblem pb, final int numTracks, final int numDetect) {
        asignOpt(pb, numTracks, numDetect);
    }

    protected void solve6(final AssignProblem pb, final int numTracks, final int numDetect) {
        asignOpt(pb, numTracks, numDetect);
    }

    public void asignOpt(final @NotNull AssignProblem pb, final int numTracks, final int numDetect) {
        final int[] tm = assignOptMax(pb.likelihoods);
        final ArrayList<Integer> usedDetection = new ArrayList<>();
        for (int j = 0; j < numTracks; j++) {
            final Track track = pb.tracks.get(j);
            if (tm[j] >= 0) {
                track.associate(pb.detections.get(tm[j]), pb.t);
                usedDetection.add(tm[j]);
            }
            else
                pb.tracksToBeProlongated.add(track);
        }
        for (int i = 0; i < numDetect; i++)
            if (!usedDetection.contains(i))
                pb.tracksToBeCreated.add(pb.detections.get(i));
    }

    @Override
    public void solve(final @NotNull AssignProblem pb) {
        final int numTracks = pb.tracks.size();
        final int numDetect = pb.detections.size();

        pb.tracksToBeCreated.clear();
        pb.tracksToBeProlongated.clear();

        // case 1 : 1 prediction faces 1 measurement
        // no conflict, the single track selects the single measurement
        if (numTracks == 1 && numDetect == 1) {
            solve1(pb);
            return;
        }

        // case 2 : 0 prediction faces 1 measurement
        // a track is created from the measurement.
        // (we can try with default profile to determine the number potential fused detections)

        if (numTracks == 0 && numDetect == 1) {
            solve2(pb);
            return;
        }
        // case 3 : 1 prediction faces 0 measurement
        // the track is prolongated with prediction
        // we can try to re-detect with profiles detection method

        if (numTracks == 1 && numDetect == 0) {
            solve3(pb);
            return;
        }

        //case 4 : n predictions for n measurements
        //simplification : there is no fused spots nor new spot, there is a one-to-one association between measurements and tracks
        if (numTracks == numDetect) {
            solve4(pb, numTracks);
        }
        //General case : n predictions != m measurements
        else {
            //case 5 : n predictions < m measurements
            if (numTracks < numDetect) {
                solve5(pb, numTracks, numDetect);
            }
            else
            //case 6 :	n predictions > m mesurements
            //we must find if some tracks has disappeared or if some has fused
            {
                solve6(pb, numTracks, numDetect);
            }
        }
    }


    protected int[] assignOptMax(final double @NotNull [] @NotNull [] d) {
        if (d.length == 0 || d[0].length == 0)
            return null;
        final int trackNumber = d[0].length;
        final int measuNumber = d.length;
        final int[] tm = new int[trackNumber]; // track-measurement associations
        final int[] t = new int[trackNumber]; // tracks indices
        final int[] m = new int[measuNumber]; // measurement indices
        for (int i = 0; i < trackNumber; i++) {
            tm[i] = -2;
            t[i] = i;
        }
        for (int i = 0; i < measuNumber; i++)
            m[i] = i;
        int iter = trackNumber;
        while (iter-- > 0) {
            int tIndex = -1;
            int mIndex = -1;
            //double opt = Double.MIN_VALUE;
            double opt = 0;
            for (int i = 0; i < trackNumber; i++) {
                if (t[i] >= 0) {
                    for (int j = 0; j < measuNumber; j++) {
                        if (m[j] >= 0) {
                            if (opt < d[j][i])// && d[j][i] >= 0)
                            {
                                tIndex = i;
                                mIndex = j;
                                opt = d[j][i];
                            }
                        }
                    }
                }
            }
            if ((tIndex != -1) && (mIndex != -1)) {
                if (opt >= 0) {
                    tm[tIndex] = mIndex;
                }
                else
                    tm[tIndex] = -1;
                t[tIndex] = -1;
                m[mIndex] = -1;
            }
        }
        return tm;
    }

    protected int[] assignOptMin(final double @NotNull [] @NotNull [] d) {
        if (d.length == 0 || d[0].length == 0)
            return null;
        final int trackNumber = d[0].length;
        final int measuNumber = d.length;
        int iter = trackNumber;
        final int[] tm = new int[trackNumber]; // track-measurement associations
        final int[] t = new int[trackNumber]; // tracks indices
        final int[] m = new int[measuNumber]; // measurement indices
        Arrays.fill(tm, -2);
        for (int i = 0; i < trackNumber; i++)
            t[i] = i;
        for (int i = 0; i < measuNumber; i++)
            m[i] = i;
        while (iter-- > 0) {
            int tIndex = -1;
            int mIndex = -1;
            double opt = Double.MAX_VALUE;
            for (int i = 0; i < trackNumber; i++) {
                if (t[i] != -1) {
                    for (int j = 0; j < measuNumber; j++) {
                        if (m[j] != -1) {
                            if (opt > d[j][i] && d[j][i] >= 0) {
                                tIndex = i;
                                mIndex = j;
                                opt = d[j][i];
                            }
                        }
                    }
                }
            }
            if ((tIndex != -1) && (mIndex != -1)) {
                if (opt >= 0) {
                    tm[tIndex] = mIndex;
                }
                else
                    tm[tIndex] = -1;
                t[tIndex] = -1;
                m[mIndex] = -1;
            }
        }
        return tm;
    }
}
