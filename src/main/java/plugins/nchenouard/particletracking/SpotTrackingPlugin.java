package plugins.nchenouard.particletracking;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

import icy.file.FileUtil;
import icy.gui.dialog.MessageDialog;
import icy.gui.frame.IcyFrame;
import icy.gui.frame.IcyInternalFrame;
import icy.gui.frame.progress.AnnounceFrame;
import icy.gui.frame.progress.ProgressFrame;
import icy.image.ImageUtil;
import icy.main.Icy;
import icy.plugin.PluginLoader;
import icy.plugin.abstract_.PluginActionable;
import icy.resource.ResourceUtil;
import icy.sequence.Sequence;
import icy.swimmingPool.SwimmingObject;
import icy.system.IcyExceptionHandler;
import icy.system.SystemUtil;
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

public class SpotTrackingPlugin extends PluginActionable implements ActionListener
{
    // public static String MHT_PLUGIN_CONFIGURATION = SpotTracking.ID_CONFIG;

    private enum GUIStateEnum
    {
        ADVANCED, SIMPLE, LEGACY
    };

    GUIStateEnum currentGUISate = GUIStateEnum.SIMPLE;

    IcyFrame mainFrame;
    JMenuBar menuBar;
    JMenu guiMenu, fileMenu, infoMenu;
    JMenuItem menuItem;

    JPanel mainPanel;

    final String SIMPLEGUI = "Simple interface";
    final String ADVANCEDGUI = "Avanced interface";
    final String LEGACYGUI = "Legacy plugin";

    TrackingMainPanel advancedGUIPanel;
    SimplifiedMHTPanel simpleGUIPanel;
    PanelTracking legacyGUIPanel;

    LegacyTrackerManager legacyTrackerManager;
    public MHTrackerManager mhTrackerManager;
    MHTparameterSet mhtParameterSet = null;
    boolean defaultParameterSet = true;

    Thread computationThread = null;
    ProgressFrame trackingAnnounce;

    public static boolean optimizationLibraryLoaded = false;

    boolean useMultithreading = true;
    boolean useLPSolve = true;

    public SpotTrackingPlugin()
    {
        if (!optimizationLibraryLoaded)
        {
            try
            {
                if (!loadLibrary("lpsolve55"))
                {
                    System.err.println("lpsolve55 cannot be loaded !");
                    throw (new Exception("lpsolve55 cannot be loaded !"));
                }
                // library have to be loaded by LpSolve itself otherwise it fails
                if (extractLibrary("lpsolve55j") == null)
                {
                    System.err.println("lpsolve55j cannot be extracted !");
                    throw (new Exception("lpsolve55j cannot be extracted !"));
                }
                // force LpSolve class loading
                Class.forName("lpsolve.LpSolve", true, PluginLoader.getLoader());
            }
            catch (Exception e)
            {
                IcyExceptionHandler.showErrorMessage(e, true);
                if (!Icy.getMainInterface().isHeadLess())
                    MessageDialog.showDialog("Warning",
                            "The lpsolve55 optimization library has failed to load:\n" + e.getMessage(),
                            MessageDialog.WARNING_MESSAGE);
                return;
            }

            optimizationLibraryLoaded = true;
        }
    }

    public File extractLibrary(String libName)
    {
        try
        {
            // get mapped library name
            String mappedlibName = System.mapLibraryName(libName);
            // get base resource path for native library
            final String basePath = getResourceLibraryPath() + FileUtil.separator;

            // search for library in resource
            URL libUrl = getResource(basePath + mappedlibName);

            // not found ?
            if (libUrl == null)
            {
                // jnilib extension may not work, try with "dylib" extension instead
                if (mappedlibName.endsWith(".jnilib"))
                {
                    mappedlibName = mappedlibName.substring(0, mappedlibName.length() - 7) + ".dylib";
                    libUrl = getResource(basePath + mappedlibName);
                }
                // do the contrary in case we have an old "jnilib" file and system use "dylib" by default
                else if (mappedlibName.endsWith(".dylib"))
                {
                    mappedlibName = mappedlibName.substring(0, mappedlibName.length() - 6) + ".jnilib";
                    libUrl = getResource(basePath + mappedlibName);
                }
            }

            // resource not found --> error
            if (libUrl == null)
                throw new IOException("Couldn't find resource " + basePath + mappedlibName);

            // extract resource
            return extractResource(SystemUtil.getTempLibraryDirectory() + FileUtil.separator + mappedlibName, libUrl);

        }
        catch (IOException e)
        {
            System.err.println("Error while extracting library " + libName + ": " + e);
            return null;
        }
    }

    @Override
    public void run()
    {
        mhTrackerManager = new MHTrackerManager();

        mainPanel = new JPanel();
        mainFrame = new IcyFrame("Spot Tracking", true, true, true, true);
        mainFrame.getContentPane().add(mainPanel);

        // set up the tracking plugin panels

        mainPanel.setLayout(new BorderLayout());

        int iconSize = 100;
        ImageIcon detectionIcon = ResourceUtil.getImageIcon(
                ImageUtil.load(
                        getResourceAsStream("plugins/nchenouard/particletracking/simplifiedMHT/detectionIcon.png")),
                iconSize);
        ImageIcon mhtIcon = ResourceUtil.getImageIcon(
                ImageUtil.load(getResourceAsStream("plugins/nchenouard/particletracking/simplifiedMHT/MHTIcon.png")),
                iconSize);
        ImageIcon outputIcon = ResourceUtil.getImageIcon(
                ImageUtil.load(
                        getResourceAsStream("plugins/nchenouard/particletracking/simplifiedMHT/trackPoolIcon.png")),
                iconSize);
        ImageIcon workingIcon = ResourceUtil.getImageIcon(
                ImageUtil
                        .load(getResourceAsStream("plugins/nchenouard/particletracking/simplifiedMHT/workingIcon.png")),
                iconSize);

        simpleGUIPanel = new SimplifiedMHTPanel(detectionIcon, mhtIcon, outputIcon, workingIcon);
        mainPanel.add(simpleGUIPanel, BorderLayout.CENTER);
        simpleGUIPanel.loadParametersButton.addActionListener(this);
        simpleGUIPanel.runTrackingButton.addActionListener(mhTrackerManager);
        simpleGUIPanel.detectionChooser.addItemListener(new ItemListener()
        {
            @Override
            public void itemStateChanged(ItemEvent e)
            {
                if (simpleGUIPanel.detectionChooser.getSelectedDetectionResult() != null && mhtParameterSet != null)
                    mhtParameterSet.detectionResults = simpleGUIPanel.detectionChooser.getSelectedDetectionResult();
            }
        });
        simpleGUIPanel.trackGroupNameTF.addKeyListener(new KeyListener()
        {
            @Override
            public void keyTyped(KeyEvent e)
            {
            }

            @Override
            public void keyReleased(KeyEvent e)
            {
                mhtParameterSet.trackGroupName = simpleGUIPanel.trackGroupNameTF.getText();
            }

            @Override
            public void keyPressed(KeyEvent e)
            {
            }
        });
        simpleGUIPanel.estimateParametersButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent arg0)
            {
                estimateTrackingParameters();
            }
        });

        advancedGUIPanel = new TrackingMainPanel();
        advancedGUIPanel.startTrackingButton.addActionListener(mhTrackerManager);
        advancedGUIPanel.configFilePanel.exportConfigurationFileButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                mhtParameterSet = advancedGUIPanel.getParameterSet();
                exportConfiguration();
            }
        });
        advancedGUIPanel.configFilePanel.loadConfigurationFileButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                loadMHTParameters();
            }
        });
        // advancedGUIPanel.configFilePanel.runMultipleConfigurationFileButton.addActionListener(new ActionListener() {
        // @Override
        // public void actionPerformed(ActionEvent e)
        // {
        // }
        // });
        refreshMainPanel(currentGUISate);

        // legacy tracker

        legacyGUIPanel = new PanelTracking();
        legacyTrackerManager = new LegacyTrackerManager(legacyGUIPanel);

        // set up the menu bar
        menuBar = new JMenuBar();
        mainFrame.setJMenuBar(menuBar);

        fileMenu = new JMenu("File");
        menuBar.add(fileMenu);

        JMenuItem saveConfigurationItem = new JMenuItem("Save parameters");
        fileMenu.add(saveConfigurationItem);
        saveConfigurationItem.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                exportConfiguration();
            }
        });

        JMenuItem loadConfigurationItem = new JMenuItem("Load parameters");
        fileMenu.add(loadConfigurationItem);
        loadConfigurationItem.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                loadMHTParameters();
            }
        });

        JMenuItem configurationItem = new JMenuItem("Plugin configuration");
        fileMenu.add(configurationItem);
        configurationItem.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                String title = "Plugin configuration";
                boolean resizable = false;
                boolean closable = true;
                boolean maximizable = false;
                boolean iconifiable = true;
                final IcyInternalFrame configFrame = new IcyInternalFrame(title, resizable, closable, maximizable,
                        iconifiable);
                JPanel configFramePanel = new JPanel();
                configFrame.setContentPane(configFramePanel);
                configFramePanel.setLayout(new GridLayout(3, 1));
                final JCheckBox multithreadingBox = new JCheckBox("Multithreaded computation.");
                multithreadingBox.setSelected(useMultithreading);
                final JCheckBox lpSolveBox = new JCheckBox("Optimization library in C (faster).");
                lpSolveBox.setSelected(useLPSolve);
                final JButton closeButton = new JButton("Apply");
                configFramePanel.add(multithreadingBox);
                configFramePanel.add(lpSolveBox);
                configFramePanel.add(closeButton);

                closeButton.addActionListener(new ActionListener()
                {
                    @Override
                    public void actionPerformed(ActionEvent e)
                    {
                        useMultithreading = multithreadingBox.isSelected();
                        useLPSolve = lpSolveBox.isSelected();
                        configFrame.close(false);
                    }
                });
                configFrame.pack();
                configFrame.setVisible(true);
                Icy.getMainInterface().addToDesktopPane(configFrame);
            }
        });

        guiMenu = new JMenu("Interface");
        menuBar.add(guiMenu);

        JMenuItem simplifiedGUIItem = new JMenuItem(SIMPLEGUI);
        guiMenu.add(simplifiedGUIItem);
        simplifiedGUIItem.addActionListener(this);

        JMenuItem advancedGUIItem = new JMenuItem(ADVANCEDGUI);
        guiMenu.add(advancedGUIItem);
        advancedGUIItem.addActionListener(this);

        JMenuItem legacyInterfaceItem = new JMenuItem(LEGACYGUI);
        guiMenu.add(legacyInterfaceItem);
        legacyInterfaceItem.addActionListener(this);

        infoMenu = new JMenu("Info");
        menuBar.add(infoMenu);

        JMenuItem manualItem = new JMenuItem("Manual");
        infoMenu.add(manualItem);
        manualItem.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                IcyFrame manualFrame = new IcyFrame("Spot Tracking Plugin Manual");
                manualFrame.setResizable(true);
                manualFrame.setClosable(true);
                JEditorPane descriptionPane = new JEditorPane();
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
            }
        });

        JMenuItem aboutItem = new JMenuItem("About");
        infoMenu.add(aboutItem);
        aboutItem.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent arg0)
            {
                IcyFrame aboutFrame = new IcyFrame("About Spot Tracking Plugin");
                aboutFrame.setResizable(true);
                aboutFrame.setClosable(true);
                JEditorPane descriptionPane = new JEditorPane();
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
            }
        });

        mainFrame.pack();
        mainFrame.addToDesktopPane();
        mainFrame.center();
        mainFrame.setVisible(true);
        mainFrame.requestFocus();

        defaultParameterSet = true;
        simpleGUIPanel.setUsingDefaultParameters(defaultParameterSet);
    }

    private void refreshMHTParameterSetFromGUI()
    {
        if (advancedGUIPanel != null)
        {
            MHTparameterSet parameters = advancedGUIPanel.getParameterSet();
            if (parameters != null)
            {
                mhtParameterSet = parameters;
                simpleGUIPanel.setParameter(mhtParameterSet);
                defaultParameterSet = false;
                simpleGUIPanel.setUsingDefaultParameters(defaultParameterSet);
            }
        }
    }

    private void setMHTParameterSetToGUI()
    {
        if (advancedGUIPanel != null && mhtParameterSet != null)
        {
            advancedGUIPanel.setParameterset(mhtParameterSet);
            simpleGUIPanel.setParameter(mhtParameterSet);
            defaultParameterSet = false;
        }
    }

    private void refreshMainPanel(final GUIStateEnum guiState)
    {
        mainPanel.removeAll();
        switch (guiState)
        {
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
    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() instanceof JMenuItem)
        {
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

    public void loadMHTParameters()
    {
        MHTparameterSet parameters = loadConfiguration();
        if (parameters != null)
        {
            // preserve the old detection results as we lost them when loading config from file
            parameters.detectionResults = mhtParameterSet.detectionResults;
            mhtParameterSet = parameters;
            setMHTParameterSetToGUI();
        }
    }

    public void exportConfiguration()
    {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileFilter()
        {
            @Override
            public String getDescription()
            {
                return "XML file filter";
            }

            @Override
            public boolean accept(File file)
            {
                if (file.isDirectory())
                    return true;
                return file.getName().endsWith(".xml");
            }
        });
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setName("Output xml file selection");
        int returnVal = fileChooser.showOpenDialog(mainPanel);
        if (returnVal == JFileChooser.APPROVE_OPTION)
        {
            String path = fileChooser.getSelectedFile().getAbsolutePath();
            if (!path.toLowerCase().endsWith(".xml"))
                path += ".xml";
            SpotTracking.saveParameters(mhtParameterSet, path);
        }
    }

    public MHTparameterSet loadConfiguration()
    {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileFilter()
        {
            @Override
            public String getDescription()
            {
                return "XML file filter";
            }

            @Override
            public boolean accept(File file)
            {
                if (file.isDirectory())
                    return true;
                return file.getName().endsWith(".xml");
            }
        });
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setName("Input xml file selection");
        int returnVal = fileChooser.showOpenDialog(mainPanel);
        if (returnVal == JFileChooser.APPROVE_OPTION)
        {
            File file = fileChooser.getSelectedFile();
            return SpotTracking.loadParameters(file);
        }

        return null;
    }

    public static void sendTracksToPool(final TrackGroup trackGroup, final Sequence sequence)
    {
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                // Add the given trackGroup
                SwimmingObject result = new SwimmingObject(trackGroup);
                Icy.getMainInterface().getSwimmingPool().add(result);
                TrackManager manager = new TrackManager();
                if (sequence != null)
                    manager.setDisplaySequence(sequence);
                new AnnounceFrame("Tracking results exported to Track manager plugin");
            }
        });
    }

    static double squaredDistance(Spot s1, Spot s2)
    {
        return SpotTracking.squaredDistance(s1, s2);
    }

    public void estimateTrackingParameters()
    {
        SwingUtilities.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                ParameterEstimationFrame estimationFrame = new ParameterEstimationFrame();
                addIcyFrame(estimationFrame);
                estimationFrame.setVisible(true);
            }
        });
    }

    public class MHTrackerManager implements ActionListener
    {
        volatile boolean isRunning = false;

        @Override
        public void actionPerformed(ActionEvent e)
        {
            if (e.getSource() == advancedGUIPanel.startTrackingButton
                    || e.getSource() == simpleGUIPanel.runTrackingButton)
            {
                if (e.getSource() == advancedGUIPanel.startTrackingButton)
                    mhtParameterSet = advancedGUIPanel.getParameterSet();
                startSoppableMHTracking();
            }
        }

        public void startSoppableMHTracking()
        {
            if (isRunning)
                return;

            computationThread = new Thread()
            {
                @Override
                public void run()
                {
                    isRunning = true;
                    enableTracking(false);
                    try
                    {
                        final TrackGroup result = SpotTracking.executeTracking(mhtParameterSet,
                                mhtParameterSet.detectionResults, useLPSolve, useMultithreading, true);
                        sendTracksToPool(result, result.getSequence());
                    }
                    catch (IllegalArgumentException e)
                    {
                        new AnnounceFrame(e.getMessage());
                        return;
                    }
                    finally
                    {
                        isRunning = false;
                        enableTracking(true);
                    }
                }
            };
            computationThread.start();
        }

        private void enableTracking(final boolean isEnableTracking)
        {
            SwingUtilities.invokeLater(new Runnable()
            {
                @Override
                public void run()
                {
                    simpleGUIPanel.changeTrackingState(!isEnableTracking);
                    advancedGUIPanel.changeTrackingState(!isEnableTracking);
                }
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

        private void startMHTracking()
        {
            // final DetectionResult dr = mhtParameterSet.detectionResults;
            // if (dr == null)
            // {
            // enableTracking(true);
            // new AnnounceFrame(
            // "Tracking requires to specify a set of detections. You can use the Spot Detector plugin to create some.",
            // 10);
            // return;
            // }
            // if (dr.getNumberOfDetection() < 1)
            // {
            // enableTracking(true);
            // new AnnounceFrame("A non empty set of detections needs to be used");
            // return;
            // }
            //
            // final HMMMHTracker mhtracker = SpotTracking.buildTracker(mhtParameterSet, dr);
            computationThread = new Thread()
            {
                @Override
                public void run()
                {
                    isRunning = true;
                    try
                    {
                        final TrackGroup result = SpotTracking.executeTracking(mhtParameterSet,
                                mhtParameterSet.detectionResults, true);
                        sendTracksToPool(result, result.getSequence());
                    }
                    catch (IllegalArgumentException e)
                    {
                        new AnnounceFrame(e.getMessage());
                        return;
                    }
                    finally
                    {

                        isRunning = false;
                        enableTracking(true);
                    }
                }
            };
            computationThread.start();
        }
    }

    class LegacyTrackerManager implements ActionListener
    {
        private StoppableTracker runningTracker = null;
        private boolean stopTracking = false;
        private boolean isRunning = false;

        private Thread runningThread = null;

        private Lock stopLock = new ReentrantLock();
        private Condition stopCondition = stopLock.newCondition();

        PanelTracking panelTracking;

        public LegacyTrackerManager(PanelTracking legacyGUIPanel)
        {
            legacyGUIPanel.trackingStartButton.addActionListener(this);
            this.panelTracking = legacyGUIPanel;
        }

        private void enableTracking(final boolean isEnableTracking)
        {
            SwingUtilities.invokeLater(new Runnable()
            {
                @Override
                public void run()
                {
                    panelTracking.changeTrackingState(!isEnableTracking);
                }
            });
        }

        public void actionPerformed(ActionEvent e)
        {
            if (e.getSource() == panelTracking.trackingStartButton)
            {
                if (isRunning)
                {
                    stopLock.lock();
                    if (runningTracker != null)
                        runningTracker.stopComputing();
                    stopTracking = true;
                    try
                    {
                        while (isRunning)
                            stopCondition.await();
                    }
                    catch (InterruptedException ie)
                    {
                        ie.printStackTrace();
                    }
                    finally
                    {
                        stopLock.unlock();
                    }
                    stopTracking = false;
                }
                else
                {
                    DetectionResult detections = panelTracking.getDetectionResults();
                    if (detections != null)
                    {
                        enableTracking(false);
                        executeTracking(detections, detections.getSequence());
                    }
                    else
                        new AnnounceFrame("Please first select a detection set.");
                }
            }
        }

        public synchronized void executeTracking(final DetectionResult detections, final Sequence sequence)
        {
            if (detections != null)
            {
                isRunning = true;
                Thread trackThread = new Thread()
                {
                    public void run()
                    {
                        int dim = 2;
                        if (sequence.getSizeZ() > 1)
                            dim = 3;
                        Tracker tracker = panelTracking.buildTracker(dim);
                        if (tracker instanceof StoppableTracker)
                            runningTracker = (StoppableTracker) tracker;
                        int firstT = detections.getFirstFrameTime();
                        int lastT = detections.getLastFrameTime();
                        String message = "Tracking at frame ";
                        ProgressFrame announceFrame = new ProgressFrame("");
                        announceFrame.setLength(lastT - firstT + 1);
                        boolean stopped = false;
                        for (int t = firstT; t <= lastT; t++)
                        {
                            if (!stopTracking)
                            {
                                announceFrame.setPosition(t);
                                announceFrame.setMessage(message + " " + t);
                                tracker.track(t, detections.getDetectionsAtT(t));
                            }
                            else
                            {
                                stopped = true;
                                break;
                            }
                        }
                        announceFrame.close();
                        if (tracker instanceof InstantaneousTracker)
                        {
                            TrackGroup trackGroup = new TrackGroup(sequence);
                            trackGroup.setDescription(panelTracking.getTrackGroupName());
                            for (Track track : ((InstantaneousTracker) tracker).getTracks())
                            {
                                TrackSegment ts = convertTrack2TrackSegment(track, null);
                                if (ts != null && ts.getDetectionList().size() > 1)
                                    trackGroup.addTrackSegment(ts);
                            }
                            sendTracksToPool(trackGroup, sequence);
                            new AnnounceFrame("Tracks exported as " + trackGroup.getDescription() + " in TrackEditor");
                        }
                        isRunning = false;
                        enableTracking(true);
                        if (stopped)
                        {
                            stopLock.lock();
                            isRunning = false;
                            stopCondition.signalAll();
                            stopLock.unlock();
                        }
                        runningThread = null;
                        runningTracker = null;
                    }
                };
                runningThread = trackThread;
                trackThread.start();
            }
        }

        public TrackSegment convertTrack2TrackSegment(Track track, Object detectionEditor)
        {
            int firstIndex = track.getFirstIndex();
            int lastIndex = track.getLastIndex();
            for (int i = lastIndex; i > firstIndex; i--)
            {
                if (track.isPredictionAtFrame(i))
                    lastIndex = i - 1;
                else
                    break;
            }
            if (firstIndex <= lastIndex)
            {
                ArrayList<Detection> detections = new ArrayList<Detection>(lastIndex - firstIndex + 1);
                for (int i = firstIndex; i <= lastIndex; i++)
                {
                    Spot s = track.getSpotAtFrame(i);
                    Detection detect = new DetectionSpotTrack(s, i);
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

    public void estimateParameters(final boolean isDirectedMotion, final boolean isSingleMotion,
            final boolean isUpdateMotion)
    {
        SpotTracking.estimateParameters(mhtParameterSet, mhtParameterSet.detectionResults, isDirectedMotion,
                isSingleMotion, isUpdateMotion);
        setMHTParameterSetToGUI();
    }

    class ParameterEstimationFrame extends IcyFrame
    {
        public ParameterEstimationFrame()
        {
            super("Parameters estimation", true, true, true, true);
            JPanel mainPanel = new JPanel();
            this.setContentPane(mainPanel);
            mainPanel.setLayout(new GridBagLayout());
            GridBagConstraints gc = new GridBagConstraints();
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
            ButtonGroup motionGroup = new ButtonGroup();
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
            ButtonGroup updateGroup = new ButtonGroup();
            updateGroup.add(noUpdateMotionButton);
            updateGroup.add(updateMotionButton);
            noUpdateMotionButton.setSelected(true);

            JButton estimateParametersButton = new JButton("Run parameter estimation procedure");
            mainPanel.add(estimateParametersButton, gc);
            estimateParametersButton.addActionListener(new ActionListener()
            {
                @Override
                public void actionPerformed(ActionEvent arg0)
                {
                    computationThread = new Thread()
                    {
                        @Override
                        public void run()
                        {
                            estimateParameters(directedButton.isSelected(), !directedDiffusiveButton.isSelected(),
                                    updateMotionButton.isSelected());
                            SwingUtilities.invokeLater(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    simpleGUIPanel.changeTrackingState(false);
                                    advancedGUIPanel.changeTrackingState(false);
                                    if (trackingAnnounce != null)
                                        trackingAnnounce.close();
                                }
                            });
                        }
                    };
                    trackingAnnounce = new ProgressFrame("Parameter estimation in progress");
                    simpleGUIPanel.changeTrackingState(true);
                    advancedGUIPanel.changeTrackingState(true);
                    computationThread.start();
                    close();
                }
            });
            this.pack();
        }
    }
}
