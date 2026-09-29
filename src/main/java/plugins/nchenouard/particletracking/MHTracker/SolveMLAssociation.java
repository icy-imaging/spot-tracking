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

package plugins.nchenouard.particletracking.MHTracker;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Very simple (and inaccurate) solver for track-to-detection problems
 * <p>
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class SolveMLAssociation {
    private static final Logger LOGGER = Logger.getLogger(SolveMLAssociation.class.getName());

    static class Track {
        Family f;
        LinkedList<MHTrack2s> association = new LinkedList<>();
        LinkedList<MHTrack2s> followingTracks = new LinkedList<>();
    }

    ArrayList<Track> trackList = new ArrayList<>();
    ArrayList<Track> currentTrackList = new ArrayList<>();

    public GlobalHypothesis getHypothesis(final Cluster cl, final int firstT, final int lastT, final HMMMHTracker tracker) {
        final GlobalHypothesis gh = new GlobalHypothesis(firstT, lastT, tracker);
        for (final Track trk : trackList) {
            final Family f = trk.f;
            Hypothesis hSelected = null;
            for (final Hypothesis h : f.hypotheses) {
                if (h.leaf == trk.association.getLast())
                    hSelected = h;
            }
            if (hSelected != null)
                gh.addHypothesis(hSelected);
            else {
                if (LOGGER.isLoggable(Level.SEVERE))
                    LOGGER.log(Level.SEVERE, "Problem in SolveMLAssociation, no selected hypothesis. " + trk.association.getLast().followingSegments.isEmpty() + " " + lastT + " " + trk.association.getLast().association.t + trk.association.getLast().confirmed);
                return null;
            }
        }
        for (int t = firstT; t <= lastT; t++) {
            cl.penalizeFD(gh, t);
            cl.penalizeNT(gh, t);
        }
        return gh;
    }

    public void solve(
            final int firstT,
            final int lastT,
            final HashMap<Integer, ArrayList<Family>> concurrentFamilies,
            final ArrayList<Family> mandatoryFamilies,
            final ArrayList<Family> optionalFamilies
    ) {
        solve2(firstT, lastT, mandatoryFamilies);
    }


    // only mandatory tracks are extended
    // the remaining detections are considered false
    public void solve2(final int firstT, final int lastT, final @NotNull ArrayList<Family> mandatoryFamilies) {
//		first create tracks from mandatory families
        final ArrayList<Association> usedAssociations = new ArrayList<>();
        for (final Family f : mandatoryFamilies) {
            final Track trk = new Track();
            trk.association.add(f.rootNode);
            trk.followingTracks = new LinkedList<>(f.rootNode.followingSegments);
            trk.f = f;
            trk.followingTracks.sort(new LowerThan());
            trackList.add(trk);
            if (!trk.followingTracks.isEmpty())
                currentTrackList.add(trk);
            else if (!f.rootNode.terminated) {
                if (LOGGER.isLoggable(Level.SEVERE))
                    LOGGER.log(Level.SEVERE, "Following tracks are empty.");
            }
        }

        for (int t = firstT + 1; t <= lastT; t++) {
            usedAssociations.clear();
            //try to extend current tracks with observed detections
            final ArrayList<Track> temp = new ArrayList<>(currentTrackList);
            // until all tracks are extended
            while (!temp.isEmpty()) {
                Track bestTrack = null;
                double bestLikelihood = 0;
                for (final Track trk : temp) {
                    if (bestTrack == null || trk.followingTracks.getFirst().associationLikelihood > bestLikelihood) {
                        bestTrack = trk;
                        bestLikelihood = trk.followingTracks.getFirst().associationLikelihood;
                    }
                }
                if (bestTrack != null) {
                    //extend best track
                    final MHTrack2s bestAssociation = bestTrack.followingTracks.getFirst();
                    usedAssociations.add(bestAssociation.association);
                    bestTrack.association.add(bestAssociation);
                    bestTrack.followingTracks = new LinkedList<>(bestAssociation.followingSegments);
                    bestTrack.followingTracks.sort(new LowerThan());

                    if (bestTrack.followingTracks.isEmpty())
                        currentTrackList.remove(bestTrack);
                    temp.remove(bestTrack);

                    //update other track following segments;
                    final ArrayList<Track> toRemove = new ArrayList<>();
                    for (final Track trk : temp) {
                        final LinkedList<MHTrack2s> copy = new LinkedList<>(trk.followingTracks);
                        for (final MHTrack2s mhtrk : copy) {
                            if (mhtrk.association == bestAssociation.association)
                                trk.followingTracks.remove(mhtrk);
                        }
                        if (trk.followingTracks.isEmpty())
                            toRemove.add(trk);
                    }
                    for (final Track trk : toRemove) {
                        currentTrackList.remove(trk);
                        temp.remove(trk);
                    }
                }
                else {
                    if (LOGGER.isLoggable(Level.SEVERE))
                        LOGGER.log(Level.SEVERE, "No best track.");
                }
            }
        }
    }

    //ne fonctionne pas toujours car on peut creer une track qui au final ne peut pas etre continuee car les associations suivantes sont deja prises
    //et les predictions ne sont pas possibles car la track n'aurait pas ete confirmee avec
    public void solve1(
            final int firstT,
            final int lastT,
            final HashMap<Integer, ArrayList<Family>> concurrentFamilies,
            final @NotNull ArrayList<Family> mandatoryFamilies,
            final ArrayList<Family> optionalFamilies
    ) {
        //first create tracks with mandatory families
        final ArrayList<Association> usedAssociations = new ArrayList<>();
        for (final Family f : mandatoryFamilies) {
            final Track trk = new Track();
            trk.association.add(f.rootNode);
            trk.followingTracks = new LinkedList<>(f.rootNode.followingSegments);
            trk.f = f;
            trk.followingTracks.sort(new LowerThan());
            usedAssociations.add(f.rootNode.association);
            trackList.add(trk);
            if (!trk.followingTracks.isEmpty())
                currentTrackList.add(trk);
        }
        //then create tracks with optional families
        for (final Family f : optionalFamilies) {
            if ((!usedAssociations.contains(f.rootNode.association)) && (!f.rootNode.association.isPrediction)) {
                usedAssociations.add(f.rootNode.association);
                final Track trk = new Track();
                trk.association.add(f.rootNode);
                trk.followingTracks = new LinkedList<>(f.rootNode.followingSegments);
                trk.f = f;
                trk.followingTracks.sort(new LowerThan());
                trackList.add(trk);
                if (!trk.followingTracks.isEmpty())
                    currentTrackList.add(trk);
            }
        }
        for (int t = firstT + 1; t <= lastT; t++) {
            usedAssociations.clear();
            //try to prolongate current tracks with observed detections
            final ArrayList<Track> temp = new ArrayList<>(currentTrackList);
            //tant que toutes les tracks n'ont pas ete prolongees
            while (!temp.isEmpty()) {
                Track bestTrack = null;
                double bestLikelihood = 0;
                for (final Track trk : temp) {
                    if (bestTrack == null || trk.followingTracks.getFirst().associationLikelihood > bestLikelihood) {
                        bestTrack = trk;
                        bestLikelihood = trk.followingTracks.getFirst().associationLikelihood;
                    }
                }
                if (bestTrack != null) {
                    //prolongate best track
                    final MHTrack2s bestAssociation = bestTrack.followingTracks.getFirst();
                    usedAssociations.add(bestAssociation.association);
                    bestTrack.association.add(bestAssociation);
                    bestTrack.followingTracks = new LinkedList<>(bestAssociation.followingSegments);
                    bestTrack.followingTracks.sort(new LowerThan());

                    if (bestTrack.followingTracks.isEmpty())
                        currentTrackList.remove(bestTrack);
                    temp.remove(bestTrack);

                    //update other track following segments;
                    final ArrayList<Track> toRemove = new ArrayList<>();
                    for (final Track trk : temp) {
                        final LinkedList<MHTrack2s> copy = new LinkedList<>(trk.followingTracks);
                        for (final MHTrack2s mhtrk : copy) {
                            if (mhtrk.association == bestAssociation.association)
                                trk.followingTracks.remove(mhtrk);
                        }
                        if (trk.followingTracks.isEmpty())
                            toRemove.add(trk);
                    }
                    for (final Track trk : toRemove) {
                        currentTrackList.remove(trk);
                        temp.remove(trk);
                    }
                }
                else {
                    if (LOGGER.isLoggable(Level.SEVERE))
                        LOGGER.log(Level.SEVERE, "No best track.");
                }
            }
            //now try to create new tracks with real association that are missing

            final ArrayList<Family> fList = concurrentFamilies.get(t);
            if (fList != null)
                for (final Family f : fList) {
                    if (!usedAssociations.contains(f.rootNode.association) && (!f.rootNode.association.isPrediction)) {
                        usedAssociations.add(f.rootNode.association);
                        final Track trk = new Track();
                        trk.association.add(f.rootNode);
                        trk.followingTracks = new LinkedList<>(f.rootNode.followingSegments);
                        trk.f = f;
                        trk.followingTracks.sort(new LowerThan());
                        trackList.add(trk);
                        if (!trk.followingTracks.isEmpty())
                            currentTrackList.add(trk);
                    }
                }
        }
    }

    static class LowerThan implements Comparator<MHTrack2s> {
        @Override
        public int compare(final @NotNull MHTrack2s t1, final MHTrack2s t2) {
            if (t1.association.isPrediction) {
                if (t2.association.isPrediction) {
                    return Double.compare(t2.associationLikelihood, t1.associationLikelihood);
                }
                else
                    return 1;
            }
            else if (t2.association.isPrediction)
                return -1;
            else {
                return Double.compare(t2.associationLikelihood, t1.associationLikelihood);
            }
        }
    }
}
