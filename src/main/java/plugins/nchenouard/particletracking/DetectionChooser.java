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

package plugins.nchenouard.particletracking;

import org.bioimageanalysis.icy.Icy;
import org.bioimageanalysis.icy.model.swimmingPool.SwimmingObject;
import org.bioimageanalysis.icy.model.swimmingPool.SwimmingPoolEvent;
import org.bioimageanalysis.icy.model.swimmingPool.SwimmingPoolListener;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import plugins.nchenouard.spot.DetectionResult;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;

/**
 * The detection chooser pick in the swimming pool the object corresponding to DetectionResult
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @author Fabrice de Chaumont, Stephane Dallongeville and Nicolas Chenouard<br>
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
public class DetectionChooser extends JComboBox<DetectionResult> implements SwimmingPoolListener {
    public interface DetectionResultChooserListener {
        void DetectionResultChanged(DetectionResult detectionResult);
    }

    private static class DetectionComboModel extends DefaultComboBoxModel<DetectionResult> {
        /**
         * items list
         */
        final ArrayList<DetectionResult> detectionResults;

        public DetectionComboModel(final ArrayList<DetectionResult> detectionResults) {
            super();

            this.detectionResults = detectionResults;
        }

        @Contract(pure = true)
        @Override
        public DetectionResult getElementAt(final int index) {
            return detectionResults.get(index);
        }

        @Contract(pure = true)
        @Override
        public int getSize() {
            return detectionResults.size();
        }
    }

    /**
     * listeners
     */
    private final ArrayList<DetectionResultChooserListener> listeners;

    /**
     * internals
     */
    private DetectionResult previousDetectionResult;

    public DetectionChooser() {
        super();

        setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            // if (value == null)
            // return new JLabel(" no detection result - Please use the Spot detector to create a detection set.");
            //
            // if (value instanceof DetectionResult )
            // return new JLabel(StringUtil.limit( value.toString() , 50 ));
            //
            // return new JLabel("");

            if (value == null)
                return new JLabel("Select detection results here");

            return new JLabel(value.toString());
        });

        Icy.getMainInterface().getSwimmingPool().addListener(this);

        addActionListener(this);

        // default
        listeners = new ArrayList<>();

        previousDetectionResult = null;
        setSelectedItem(null);

        // refresh list
        refreshDetectionResultList();

        // fix height
        // ComponentUtil.setFixedHeight(this, 26);
    }

    /**
     * @return current detectionResult selected in combo. null if no sequence is selected or if the
     * sequence do not exists anymore.
     */
    public DetectionResult getSelectedDetectionResult() {
        return (DetectionResult) getSelectedItem();
    }

    /**
     * @param detectionResult The detectionResult to select in the combo box.
     */
    public void setDetectionResultSelected(final DetectionResult detectionResult) {
        if (detectionResult != getSelectedDetectionResult()) {
            setSelectedItem(detectionResult);
        }
    }

    void refreshDetectionResultList() {
        // save old selection
        final Object oldSelected = getSelectedItem();
        // rebuild model
        setModel(new DetectionComboModel(getDetectionResultList()));
        // restore selection
        setSelectedItem(oldSelected);
    }

    private @NotNull ArrayList<DetectionResult> getDetectionResultList() {

        final ArrayList<DetectionResult> detectionResultList = new ArrayList<>();

        final ArrayList<SwimmingObject> objects = Icy.getMainInterface().getSwimmingPool().getObjects();

        for (final SwimmingObject so : objects) {
            //IcyLogger.debug(this.getClass(), so.getObject().toString());
            final Object o = so.getObject();
            //IcyLogger.debug(this.getClass(), String.valueOf(o instanceof DetectionResult));
            if (so.getObject() instanceof DetectionResult) {
                //IcyLogger.debug(this.getClass(), "Object added: " + so.getObject() );
                detectionResultList.add((DetectionResult) so.getObject());
            }
        }

        return detectionResultList;
    }

    /**
     * called when the selection has changed
     */
    private void detectionResultChanged(final DetectionResult detectionResult) {
        fireDetectionResultChanged(detectionResult);
    }

    private void fireDetectionResultChanged(final DetectionResult detectionResult) {
        final ArrayList<DetectionResultChooserListener> listenersCopy = getListeners();

        for (final DetectionResultChooserListener listener : listenersCopy)
            listener.DetectionResultChanged(detectionResult);
    }

    public ArrayList<DetectionResultChooserListener> getListeners() {
        return new ArrayList<>(listeners);
    }

    public void addListener(final DetectionResultChooserListener listener) {
        if (!listeners.contains(listener))
            listeners.add(listener);
    }

    public void removeListener(final DetectionResultChooserListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
        final DetectionResult detectionResult = getSelectedDetectionResult();

        if (previousDetectionResult != detectionResult) {
            previousDetectionResult = detectionResult;
            detectionResultChanged(detectionResult);
        }
    }

    @Override
    public void swimmingPoolChangeEvent(final SwimmingPoolEvent event) {
        // Thread.in
        SwingUtilities.invokeLater(() -> {
            refreshDetectionResultList();

            // Select the last entry computed
            if (event.getResult().getObject() instanceof DetectionResult) {
                setSelectedItem(event.getResult().getObject());
            }
        });
    }

}