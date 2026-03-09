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

package plugins.nchenouard.particletracking;

import org.bioimageanalysis.icy.Icy;
import org.bioimageanalysis.icy.extension.plugin.abstract_.PluginActionable;
import org.bioimageanalysis.icy.extension.plugin.annotation_.IcyPluginName;
import org.bioimageanalysis.icy.gui.frame.IcyFrame;
import org.bioimageanalysis.icy.gui.frame.IcyInternalFrame;
import org.bioimageanalysis.icy.gui.frame.progress.AnnounceFrame;
import org.bioimageanalysis.icy.gui.frame.progress.ProgressFrame;
import org.bioimageanalysis.icy.model.image.ImageUtil;
import org.bioimageanalysis.icy.model.sequence.Sequence;
import org.bioimageanalysis.icy.model.swimmingPool.SwimmingObject;
import org.bioimageanalysis.icy.system.logging.IcyLogger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import plugins.fab.trackmanager.TrackGroup;
import plugins.fab.trackmanager.TrackManager;
import plugins.fab.trackmanager.TrackSegment;
import plugins.nchenouard.particletracking.gui.TrackingMainPanel;
import plugins.nchenouard.particletracking.legacytracker.StoppableTracker;
import plugins.nchenouard.particletracking.legacytracker.Tracker;
import plugins.nchenouard.particletracking.legacytracker.associationMethod.InstantaneousTracker;
import plugins.nchenouard.particletracking.legacytracker.associationMethod.Track;
import plugins.nchenouard.particletracking.legacytracker.gui.PanelTracking;
import plugins.nchenouard.particletracking.simplifiedMHT.SimplifiedMHTPanel;
import plugins.nchenouard.spot.Detection;
import plugins.nchenouard.spot.DetectionResult;
import plugins.nchenouard.spot.Spot;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Particle tracking plugin for ICY that uses the multiple hypothesis tracking algorithm
 * described in:
 * Nicolas Chenouard, Isabelle Bloch, Jean-Christophe Olivo-Marin,
 * "Multiple Hypothesis Tracking for Cluttered Biological Image Sequences," IEEE Transactions on Pattern Analysis and
 * Machine Intelligence, vol. 35, no. 11, pp. 2736-3750, Nov., 2013
 * Pubmed link: http://www.ncbi.nlm.nih.gov/pubmed/23689865
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 *
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */
@IcyPluginName("Spot Tracking")
public class SpotTrackingPlugin extends PluginActionable implements ActionListener {
    // public static String MHT_PLUGIN_CONFIGURATION = SpotTracking.ID_CONFIG;

    private enum GUIStateEnum {
        ADVANCED, SIMPLE, LEGACY
    }

    private GUIStateEnum currentGUISate = GUIStateEnum.SIMPLE;

    private IcyFrame mainFrame;

    private JPanel mainPanel;

    private final String SIMPLEGUI = "Simple interface";
    private final String ADVANCEDGUI = "Avanced interface";
    private final String LEGACYGUI = "Legacy plugin";

    private TrackingMainPanel advancedGUIPanel;
    private SimplifiedMHTPanel simpleGUIPanel;
    private PanelTracking legacyGUIPanel;

    private MHTparameterSet mhtParameterSet = null;
    private boolean defaultParameterSet = true;

    private Thread computationThread = null;
    private ProgressFrame trackingAnnounce;

    private boolean useMultithreading = true;

    @Override
    public void run() {
        final MHTrackerManager mhTrackerManager = new MHTrackerManager();

        mainPanel = new JPanel();
        mainFrame = new IcyFrame("Spot Tracking", true, true, true, true);
        mainFrame.getContentPane().add(mainPanel);

        // set up the tracking plugin panels

        mainPanel.setLayout(new BorderLayout());

        final int iconSize = 100;
        final ImageIcon detectionIcon = new ImageIcon(
                ImageUtil.load(getResourceAsStream("plugins/nchenouard/particletracking/simplifiedMHT/detectionIcon.png"))
                        .getScaledInstance(iconSize, iconSize, Image.SCALE_SMOOTH)
        );
        final ImageIcon mhtIcon = new ImageIcon(
                ImageUtil.load(getResourceAsStream("plugins/nchenouard/particletracking/simplifiedMHT/MHTIcon.png"))
                        .getScaledInstance(iconSize, iconSize, Image.SCALE_SMOOTH)
        );
        final ImageIcon outputIcon = new ImageIcon(
                ImageUtil.load(getResourceAsStream("plugins/nchenouard/particletracking/simplifiedMHT/trackPoolIcon.png"))
                        .getScaledInstance(iconSize, iconSize, Image.SCALE_SMOOTH)
        );
        final ImageIcon workingIcon = new ImageIcon(
                ImageUtil.load(getResourceAsStream("plugins/nchenouard/particletracking/simplifiedMHT/workingIcon.png"))
                        .getScaledInstance(iconSize, iconSize, Image.SCALE_SMOOTH)
        );

        simpleGUIPanel = new SimplifiedMHTPanel(detectionIcon, mhtIcon, outputIcon, workingIcon);
        mainPanel.add(simpleGUIPanel, BorderLayout.CENTER);
        simpleGUIPanel.loadParametersButton.addActionListener(this);
        simpleGUIPanel.runTrackingButton.addActionListener(mhTrackerManager);
        simpleGUIPanel.detectionChooser.addItemListener(e -> {
            if (simpleGUIPanel.detectionChooser.getSelectedDetectionResult() != null && mhtParameterSet != null)
                mhtParameterSet.detectionResults = simpleGUIPanel.detectionChooser.getSelectedDetectionResult();
        });
        simpleGUIPanel.trackGroupNameTF.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(final KeyEvent e) {
            }

            @Override
            public void keyReleased(final KeyEvent e) {
                mhtParameterSet.trackGroupName = simpleGUIPanel.trackGroupNameTF.getText();
            }

            @Override
            public void keyPressed(final KeyEvent e) {
            }
        });
        simpleGUIPanel.estimateParametersButton.addActionListener(arg0 -> estimateTrackingParameters());

        advancedGUIPanel = new TrackingMainPanel();
        advancedGUIPanel.startTrackingButton.addActionListener(mhTrackerManager);
        advancedGUIPanel.configFilePanel.exportConfigurationFileButton.addActionListener(e -> {
            mhtParameterSet = advancedGUIPanel.getParameterSet();
            exportConfiguration();
        });
        advancedGUIPanel.configFilePanel.loadConfigurationFileButton.addActionListener(e -> loadMHTParameters());
        // advancedGUIPanel.configFilePanel.runMultipleConfigurationFileButton.addActionListener(new ActionListener() {
        // @Override
        // public void actionPerformed(ActionEvent e)
        // {
        // }
        // });
        refreshMainPanel(currentGUISate);

        // legacy tracker

        legacyGUIPanel = new PanelTracking();
        /*final LegacyTrackerManager legacyTrackerManager = */
        new LegacyTrackerManager(legacyGUIPanel);

        // set up the menu bar
        final JMenuBar menuBar = new JMenuBar();
        mainFrame.setJMenuBar(menuBar);

        final JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);

        final JMenuItem saveConfigurationItem = new JMenuItem("Save parameters");
        fileMenu.add(saveConfigurationItem);
        saveConfigurationItem.addActionListener(e -> exportConfiguration());

        final JMenuItem loadConfigurationItem = new JMenuItem("Load parameters");
        fileMenu.add(loadConfigurationItem);
        loadConfigurationItem.addActionListener(e -> loadMHTParameters());

        final JMenuItem configurationItem = new JMenuItem("Plugin configuration");
        fileMenu.add(configurationItem);
        configurationItem.addActionListener(e -> {
            final String title = "Plugin configuration";
            final boolean resizable = false;
            final boolean closable = true;
            final boolean maximizable = false;
            final boolean iconifiable = true;
            final IcyInternalFrame configFrame = new IcyInternalFrame(title, resizable, closable, maximizable, iconifiable);
            final JPanel configFramePanel = new JPanel();
            configFrame.setContentPane(configFramePanel);
            configFramePanel.setLayout(new GridLayout(3, 1));
            final JCheckBox multithreadingBox = new JCheckBox("Multithreaded computation.");
            multithreadingBox.setSelected(useMultithreading);
            final JButton closeButton = new JButton("Apply");
            configFramePanel.add(multithreadingBox);
            configFramePanel.add(closeButton);

            closeButton.addActionListener(e1 -> {
                useMultithreading = multithreadingBox.isSelected();
                configFrame.close(false);
            });
            configFrame.pack();
            configFrame.setVisible(true);
            Icy.getMainInterface().addToDesktopPane(configFrame);
        });

        final JMenu guiMenu = new JMenu("Interface");
        menuBar.add(guiMenu);

        final JMenuItem simplifiedGUIItem = new JMenuItem(SIMPLEGUI);
        guiMenu.add(simplifiedGUIItem);
        simplifiedGUIItem.addActionListener(this);

        final JMenuItem advancedGUIItem = new JMenuItem(ADVANCEDGUI);
        guiMenu.add(advancedGUIItem);
        advancedGUIItem.addActionListener(this);

        final JMenuItem legacyInterfaceItem = new JMenuItem(LEGACYGUI);
        guiMenu.add(legacyInterfaceItem);
        legacyInterfaceItem.addActionListener(this);

        final JMenu infoMenu = new JMenu("Info");
        menuBar.add(infoMenu);

        final JMenuItem manualItem = new JMenuItem("Manual");
        infoMenu.add(manualItem);
        manualItem.addActionListener(e -> {
            final IcyFrame manualFrame = new IcyFrame("Spot Tracking Plugin Manual");
            manualFrame.setResizable(true);
            manualFrame.setClosable(true);
            final JEditorPane descriptionPane = new JEditorPane();
            descriptionPane.setEditable(false);
            descriptionPane.setContentType("text/html");
            descriptionPane.setText(
                    "<html><p>For a full description, please read the <a href=http://icy.bioimageanalysis.org/plugin/Spot_Tracking#documentation>online manual</a> on ICY website:<br> http://icy.bioimageanalysis.org/plugin/Spot_Tracking#documentation"
                            + "<p><b>Quick description:</b>"
                            + "<br><html>The usual way of using the software is to follow indications given by the interface from top to bottom.<br> Tool tip text on each graphical element gives useful information about the use."
                            + "<br>In general, the following sequence of operations will be performed: <ol><li>Select a set of locations in space and over time (detections) for the spots to track.<br> This can be done with the Spot Detector plugin. (Section 1)</li> <li>Estimate parameters for the tracking steps or load a set of existing parameters from a local file. (Section 2)</li><li>Specify a unique name for the tracking results. (Section 3)</li><li>Run the track construction process. (Section 4)</li><li>Analyze and save tracking results with the Track Manager plugin.</li><li>Save the tracking parameters via the File menu of the plugin for future re-use.</li></ol>"
                            + "<br>Exhaustive control of the tracking parameters is accessible by switching to the advanced interface.<br> (Menu Interface/Advanced Interface).</html>");
            manualFrame.setContentPane(descriptionPane);
            manualFrame.pack();
            addIcyFrame(manualFrame);
            manualFrame.setVisible(true);
        });

        final JMenuItem aboutItem = new JMenuItem("About");
        infoMenu.add(aboutItem);
        aboutItem.addActionListener(arg0 -> {
            final IcyFrame aboutFrame = new IcyFrame("About Spot Tracking Plugin");
            aboutFrame.setResizable(true);
            aboutFrame.setClosable(true);
            final JEditorPane descriptionPane = new JEditorPane();
            descriptionPane.setEditable(false);
            descriptionPane.setContentType("text/html");
            descriptionPane.setText(
                    "<html><p>The Spot Tracking Plugin is a essentially an implementation of the methods described in the article:"
                            + "<br>Nicolas Chenouard, Isabelle Bloch, Jean-Christophe Olivo-Marin,<br> <b>Multiple Hypothesis Tracking for Cluttered Biological Image Sequences</b>, <br>IEEE Transactions on Pattern Analysis and Machine Intelligence, vol. 35, no. 11, pp. 2736-3750, Nov., 2013"
                            + "<br>Pubmed link: http://www.ncbi.nlm.nih.gov/pubmed/23689865"
                            + "<br><p>By using the software for data analysis you agree to properly cite and reference this work<br> in any communication regarding results obtained by using it."
                            + "<br><p>Author: Nicolas Chenouard. Institut Pasteur, Paris, France."
                            + "<br>Version 3.1, 2013-11-13.</html>");
            aboutFrame.setContentPane(descriptionPane);
            aboutFrame.pack();
            addIcyFrame(aboutFrame);
            aboutFrame.setVisible(true);
        });

        mainFrame.pack();
        mainFrame.addToDesktopPane();
        mainFrame.center();
        mainFrame.setVisible(true);
        mainFrame.requestFocus();

        defaultParameterSet = true;
        simpleGUIPanel.setUsingDefaultParameters(defaultParameterSet);
    }

    private void refreshMHTParameterSetFromGUI() {
        if (advancedGUIPanel != null) {
            final MHTparameterSet parameters = advancedGUIPanel.getParameterSet();
            if (parameters != null) {
                mhtParameterSet = parameters;
                simpleGUIPanel.setParameter(mhtParameterSet);
                defaultParameterSet = false;
                simpleGUIPanel.setUsingDefaultParameters(defaultParameterSet);
            }
        }
    }

    private void setMHTParameterSetToGUI() {
        if (advancedGUIPanel != null && mhtParameterSet != null) {
            advancedGUIPanel.setParameterset(mhtParameterSet);
            simpleGUIPanel.setParameter(mhtParameterSet);
            defaultParameterSet = false;
        }
    }

    private void refreshMainPanel(final @NotNull GUIStateEnum guiState) {
        mainPanel.removeAll();
        switch (guiState) {
            case ADVANCED:
                setMHTParameterSetToGUI(); // set the current parameters to the advanced GUI
                mainPanel.add(advancedGUIPanel, BorderLayout.CENTER);
                break;
            case LEGACY:
                mainPanel.add(legacyGUIPanel, BorderLayout.CENTER);
                break;
            case SIMPLE:
                refreshMHTParameterSetFromGUI(); // load parameters from advanced GUI
                mainPanel.add(simpleGUIPanel, BorderLayout.CENTER);
                break;
            default:
                break;
        }
        mainFrame.pack();
        mainFrame.updateUI();
        currentGUISate = guiState;
    }

    @Override
    public void actionPerformed(final @NotNull ActionEvent e) {
        if (e.getSource() instanceof JMenuItem) {
            if (((JMenuItem) e.getSource()).getText().equals(SIMPLEGUI))
                refreshMainPanel(GUIStateEnum.SIMPLE); // switch to simple GUI
            if (((JMenuItem) e.getSource()).getText().equals(ADVANCEDGUI))
                refreshMainPanel(GUIStateEnum.ADVANCED); // switch to advanced GUI
            if (((JMenuItem) e.getSource()).getText().equals(LEGACYGUI))
                refreshMainPanel(GUIStateEnum.LEGACY); // switch to legacy GUI
        }
        if (e.getSource() == simpleGUIPanel.loadParametersButton)
            loadMHTParameters();
    }

    public void loadMHTParameters() {
        final MHTparameterSet parameters = loadConfiguration();
        if (parameters != null) {
            // preserve the old detection results as we lost them when loading config from file
            parameters.detectionResults = mhtParameterSet.detectionResults;
            mhtParameterSet = parameters;
            setMHTParameterSetToGUI();
        }
    }

    public void exportConfiguration() {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileFilter() {
            @Override
            public String getDescription() {
                return "XML file filter";
            }

            @Override
            public boolean accept(final File file) {
                if (file.isDirectory())
                    return true;
                return file.getName().endsWith(".xml");
            }
        });
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setName("Output xml file selection");
        final int returnVal = fileChooser.showOpenDialog(mainPanel);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            String path = fileChooser.getSelectedFile().getAbsolutePath();
            if (!path.toLowerCase().endsWith(".xml"))
                path += ".xml";
            SpotTracking.saveParameters(mhtParameterSet, path);
        }
    }

    public MHTparameterSet loadConfiguration() {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileFilter() {
            @Override
            public String getDescription() {
                return "XML file filter";
            }

            @Override
            public boolean accept(final File file) {
                if (file.isDirectory())
                    return true;
                return file.getName().endsWith(".xml");
            }
        });
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setName("Input xml file selection");
        final int returnVal = fileChooser.showOpenDialog(mainPanel);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            final File file = fileChooser.getSelectedFile();
            return SpotTracking.loadParameters(file);
        }

        return null;
    }

    @SuppressWarnings("resource")
    public static void sendTracksToPool(final TrackGroup trackGroup, final Sequence sequence) {
        SwingUtilities.invokeLater(() -> {
            // Add the given trackGroup
            final SwimmingObject result = new SwimmingObject(trackGroup);
            Icy.getMainInterface().getSwimmingPool().add(result);
            final TrackManager manager = new TrackManager();
            if (sequence != null)
                manager.setDisplaySequence(sequence);
            new AnnounceFrame("Tracking results exported to Track manager plugin");
        });
    }

    @Contract(pure = true)
    static double squaredDistance(final Spot s1, final Spot s2) {
        return SpotTracking.squaredDistance(s1, s2);
    }

    public void estimateTrackingParameters() {
        SwingUtilities.invokeLater(() -> {
            final ParameterEstimationFrame estimationFrame = new ParameterEstimationFrame();
            addIcyFrame(estimationFrame);
            estimationFrame.setVisible(true);
        });
    }

    public class MHTrackerManager implements ActionListener {
        volatile boolean isRunning = false;

        @Override
        public void actionPerformed(final @NotNull ActionEvent e) {
            if (e.getSource() == advancedGUIPanel.startTrackingButton
                    || e.getSource() == simpleGUIPanel.runTrackingButton) {
                if (e.getSource() == advancedGUIPanel.startTrackingButton)
                    mhtParameterSet = advancedGUIPanel.getParameterSet();
                startSoppableMHTracking();
            }
        }

        public void startSoppableMHTracking() {
            if (isRunning)
                return;

            computationThread = new Thread(() -> {
                isRunning = true;
                enableTracking(false);
                try {
                    final TrackGroup result = SpotTracking.executeTracking(mhtParameterSet, mhtParameterSet.detectionResults, useMultithreading, true);
                    sendTracksToPool(result, result.getSequence());
                }
                catch (final IllegalArgumentException e) {
                    new AnnounceFrame(e.getMessage());
                }
                finally {
                    isRunning = false;
                    enableTracking(true);
                }
            });
            computationThread.start();
        }

        private void enableTracking(final boolean isEnableTracking) {
            SwingUtilities.invokeLater(() -> {
                simpleGUIPanel.changeTrackingState(!isEnableTracking);
                advancedGUIPanel.changeTrackingState(!isEnableTracking);
            });
        }

        // public HMMMHTracker buildMHTracker(DetectionResult dr)
        // {
        // HMMMHTracker tracker = null;
        // int dim = 2;
        // double volume = 256 * 256;
        // if (dr.getSequence() != null)
        // {
        // if (dr.getSequence().getSizeZ() > 1)
        // dim = 3;
        // volume = dr.getSequence().getSizeX() * dr.getSequence().getSizeY() * dr.getSequence().getSizeZ();
        // }
        // else
        // {
        // // check detections to see if they all have the same z coordinate
        // // double minX = Double.MAX_VALUE;
        // // double minY = Double.MAX_VALUE;
        // // double minZ = Double.MAX_VALUE;
        // double minX = 0; // assume the sequence coordinates start at 0
        // double minY = 0;
        // double minZ = 0;
        // double maxX = 0;
        // double maxY = 0;
        // double maxZ = 0;
        //
        // for (int t = dr.getFirstFrameTime(); t <= dr.getLastFrameTime(); t++)
        // {
        // for (Spot s : dr.getDetectionsAtT(t))
        // {
        // minX = Math.min(minX, s.mass_center.x);
        // minY = Math.min(minY, s.mass_center.y);
        // minZ = Math.min(minZ, s.mass_center.z);
        // maxX = Math.max(maxX, s.mass_center.x);
        // maxY = Math.max(maxY, s.mass_center.y);
        // maxZ = Math.max(maxZ, s.mass_center.z);
        // }
        // }
        // if (maxZ > minZ)
        // {
        // dim = 3;
        // volume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        // }
        // else
        // {
        // dim = 2;
        // volume = (maxX - minX + 1) * (maxY - minY + 1);
        // }
        // }
        // try
        // {
        // tracker = advancedGUIPanel.buildTracker(dim, volume, useLPSolve && optimizationLibraryLoaded,
        // useMultithreading);
        // }
        // catch (Exception e)
        // {
        // e.printStackTrace();
        // }
        // return tracker;
        // }
    }

    static class LegacyTrackerManager implements ActionListener {
        private StoppableTracker runningTracker = null;
        private boolean stopTracking = false;
        private boolean isRunning = false;

        //private Thread runningThread = null;

        private final Lock stopLock = new ReentrantLock();
        private final Condition stopCondition = stopLock.newCondition();

        PanelTracking panelTracking;

        public LegacyTrackerManager(final @NotNull PanelTracking legacyGUIPanel) {
            legacyGUIPanel.trackingStartButton.addActionListener(this);
            this.panelTracking = legacyGUIPanel;
        }

        private void enableTracking(final boolean isEnableTracking) {
            SwingUtilities.invokeLater(() -> panelTracking.changeTrackingState(!isEnableTracking));
        }

        @Override
        public void actionPerformed(final @NotNull ActionEvent e) {
            if (e.getSource() == panelTracking.trackingStartButton) {
                if (isRunning) {
                    stopLock.lock();
                    if (runningTracker != null)
                        runningTracker.stopComputing();
                    stopTracking = true;
                    try {
                        while (isRunning)
                            stopCondition.await();
                    }
                    catch (final InterruptedException ie) {
                        IcyLogger.error(this.getClass(), ie);
                    }
                    finally {
                        stopLock.unlock();
                    }
                    stopTracking = false;
                }
                else {
                    final DetectionResult detections = panelTracking.getDetectionResults();
                    if (detections != null) {
                        enableTracking(false);
                        executeTracking(detections, detections.getSequence());
                    }
                    else
                        new AnnounceFrame("Please first select a detection set.");
                }
            }
        }

        public synchronized void executeTracking(final DetectionResult detections, final Sequence sequence) {
            if (detections != null) {
                isRunning = true;
                final Thread trackThread = new Thread(() -> {
                    int dim = 2;
                    if (sequence.getSizeZ() > 1)
                        dim = 3;
                    final Tracker tracker = panelTracking.buildTracker(dim);
                    if (tracker instanceof StoppableTracker)
                        runningTracker = (StoppableTracker) tracker;
                    final int firstT = detections.getFirstFrameTime();
                    final int lastT = detections.getLastFrameTime();
                    final String message = "Tracking at frame ";
                    final ProgressFrame announceFrame = new ProgressFrame("");
                    announceFrame.setLength(lastT - firstT + 1);
                    boolean stopped = false;
                    for (int t = firstT; t <= lastT; t++) {
                        if (!stopTracking) {
                            announceFrame.setPosition(t);
                            announceFrame.setMessage(message + " " + t);
                            tracker.track(t, detections.getDetectionsAtT(t));
                        }
                        else {
                            stopped = true;
                            break;
                        }
                    }
                    announceFrame.close();
                    if (tracker instanceof InstantaneousTracker) {
                        final TrackGroup trackGroup = new TrackGroup(sequence);
                        trackGroup.setDescription(panelTracking.getTrackGroupName());
                        for (final Track track : ((InstantaneousTracker) tracker).getTracks()) {
                            final TrackSegment ts = convertTrack2TrackSegment(track, null);
                            if (ts != null && ts.getDetectionList().size() > 1)
                                trackGroup.addTrackSegment(ts);
                        }
                        sendTracksToPool(trackGroup, sequence);
                        new AnnounceFrame("Tracks exported as " + trackGroup.getDescription() + " in TrackEditor");
                    }
                    isRunning = false;
                    enableTracking(true);
                    if (stopped) {
                        stopLock.lock();
                        isRunning = false;
                        stopCondition.signalAll();
                        stopLock.unlock();
                    }
                    //runningThread = null;
                    runningTracker = null;
                });
                //runningThread = trackThread;
                trackThread.start();
            }
        }

        public TrackSegment convertTrack2TrackSegment(final @NotNull Track track, final Object detectionEditor) {
            final int firstIndex = track.getFirstIndex();
            int lastIndex = track.getLastIndex();
            for (int i = lastIndex; i > firstIndex; i--) {
                if (track.isPredictionAtFrame(i))
                    lastIndex = i - 1;
                else
                    break;
            }
            if (firstIndex <= lastIndex) {
                final ArrayList<Detection> detections = new ArrayList<>(lastIndex - firstIndex + 1);
                for (int i = firstIndex; i <= lastIndex; i++) {
                    final Spot s = track.getSpotAtFrame(i);
                    final Detection detect = new DetectionSpotTrack(s, i);
                    if (track.isPredictionAtFrame(i))
                        detect.setDetectionType(Detection.DETECTIONTYPE_VIRTUAL_DETECTION);
                    detections.add(detect);
                }
                return new TrackSegment(detections);
            }
            else
                return null;
        }

    }

    public void estimateParameters(final boolean isDirectedMotion, final boolean isSingleMotion, final boolean isUpdateMotion) {
        SpotTracking.estimateParameters(mhtParameterSet, mhtParameterSet.detectionResults, isDirectedMotion, isSingleMotion, isUpdateMotion);
        setMHTParameterSetToGUI();
    }

    class ParameterEstimationFrame extends IcyFrame {
        public ParameterEstimationFrame() {
            super("Parameters estimation", true, true, true, true);
            final JPanel mainPanel = new JPanel();
            this.setContentPane(mainPanel);
            mainPanel.setLayout(new GridBagLayout());
            final GridBagConstraints gc = new GridBagConstraints();
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.gridy = 0;
            mainPanel.add(new JLabel("Target motion"), gc);
            gc.gridy++;
            final JRadioButton diffusiveButton = new JRadioButton("is mainly diffusive (default).");
            mainPanel.add(diffusiveButton, gc);
            gc.gridy++;
            final JRadioButton directedButton = new JRadioButton("is mainly directed.");
            mainPanel.add(directedButton, gc);
            gc.gridy++;
            final JRadioButton directedDiffusiveButton = new JRadioButton("is both diffusive and directed.");
            mainPanel.add(directedDiffusiveButton, gc);
            gc.gridy++;
            final ButtonGroup motionGroup = new ButtonGroup();
            motionGroup.add(diffusiveButton);
            motionGroup.add(directedButton);
            motionGroup.add(directedDiffusiveButton);
            diffusiveButton.setSelected(true);

            mainPanel.add(new JLabel("Parameters for target motion"), gc);
            gc.gridy++;
            final JRadioButton noUpdateMotionButton = new JRadioButton("are kept to their initial values (default).");
            mainPanel.add(noUpdateMotionButton, gc);
            gc.gridy++;
            final JRadioButton updateMotionButton = new JRadioButton("are re-estimated online.");
            mainPanel.add(updateMotionButton, gc);
            gc.gridy++;
            final ButtonGroup updateGroup = new ButtonGroup();
            updateGroup.add(noUpdateMotionButton);
            updateGroup.add(updateMotionButton);
            noUpdateMotionButton.setSelected(true);

            final JButton estimateParametersButton = new JButton("Run parameter estimation procedure");
            mainPanel.add(estimateParametersButton, gc);
            estimateParametersButton.addActionListener(arg0 -> {
                computationThread = new Thread(() -> {
                    estimateParameters(directedButton.isSelected(), !directedDiffusiveButton.isSelected(), updateMotionButton.isSelected());
                    SwingUtilities.invokeLater(() -> {
                        simpleGUIPanel.changeTrackingState(false);
                        advancedGUIPanel.changeTrackingState(false);
                        if (trackingAnnounce != null)
                            trackingAnnounce.close();
                    });
                });
                trackingAnnounce = new ProgressFrame("Parameter estimation in progress");
                simpleGUIPanel.changeTrackingState(true);
                advancedGUIPanel.changeTrackingState(true);
                computationThread.start();
                close();
            });
            this.pack();
        }
    }
}
