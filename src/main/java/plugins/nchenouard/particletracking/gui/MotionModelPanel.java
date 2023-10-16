package plugins.nchenouard.particletracking.gui;

import icy.gui.frame.progress.AnnounceFrame;
import icy.util.XMLUtil;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import plugins.nchenouard.particletracking.MHTparameterSet;

/**
 * Panel for setting the motion models of the targets to track
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @version 3.1
 * date 2013-11-13
 * license gpl v3.0
 */

public class MotionModelPanel extends JPanel
{
    private static final long serialVersionUID = 30906998503570435L;
    DefaultMutableTreeNode node = new DefaultMutableTreeNode("Motion model");

    final String singleMotionString = "Single motion model";
    final String multipleMotionString = "Multiple motion models";
    final JComboBox filtersBox = new JComboBox(new String[] {singleMotionString, multipleMotionString});
    final SingleMotionPanel singleMotionPanel = new SingleMotionPanel();
    final MultipleMotionPanel multipleMotionPanel = new MultipleMotionPanel();

    final CardLayout motionCardLayout = new CardLayout();
    final JPanel motionModelPanel = new JPanel(motionCardLayout);

    public MotionModelPanel()
    {
        this.setLayout(new BorderLayout());

        JPanel pane = new JPanel();
        GridBagLayout gbl = new GridBagLayout();
        pane.setLayout(gbl);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.weighty = 0.0;
        c.gridwidth = 1;
        c.insets = new Insets(2, 2, 2, 2);

        JPanel mPanel = new JPanel(new BorderLayout());
        mPanel.add(motionModelPanel, BorderLayout.CENTER);
        motionModelPanel.add(singleMotionPanel, singleMotionString);
        motionModelPanel.add(multipleMotionPanel, multipleMotionString);
        filtersBox.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                motionCardLayout.show(motionModelPanel, filtersBox.getSelectedItem().toString());
            }
        });

        c.gridx = 0;
        c.gridy = 0;
        pane.add(filtersBox, c);
        c.gridy++;
        pane.add(mPanel, c);

        this.add(pane, BorderLayout.NORTH);
    }

    public class SingleMotionPanel extends JPanel
    {
        /**
		 * 
		 */
        private static final long serialVersionUID = 3216670506933002101L;
        final KFRandomWalkPanel diffusionPanel = new KFRandomWalkPanel();
        final KFDirectedPanel directedPanel = new KFDirectedPanel();
        final JCheckBox useDirectedMotionBox = new JCheckBox("Use directed motion");
        final JCheckBox displacementReestimateBox = new JCheckBox("Re-estimate motion length online");

        public SingleMotionPanel()
        {
            JPanel pane = this;
            GridBagLayout gbl = new GridBagLayout();
            pane.setLayout(gbl);
            GridBagConstraints c = new GridBagConstraints();
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1.0;
            c.weighty = 0.0;
            c.gridwidth = 1;
            c.insets = new Insets(2, 2, 2, 2);

            c.gridx = 0;
            c.gridy = 0;
            diffusionPanel.setBorder(new TitledBorder("Diffusion"));
            pane.add(diffusionPanel, c);

            c.gridx = 0;
            c.gridy = 1;
            useDirectedMotionBox.addActionListener(new ActionListener()
            {
                @Override
                public void actionPerformed(ActionEvent arg0)
                {
                    directedPanel.setEnabled(useDirectedMotionBox.isSelected());
                }
            });
            useDirectedMotionBox.setSelected(MHTparameterSet.defaultIsDirectedMotion);
            directedPanel.setEnabled(useDirectedMotionBox.isSelected());
            pane.add(useDirectedMotionBox, c);

            c.gridx = 0;
            c.gridy = 2;
            directedPanel.setBorder(new TitledBorder("Directed motion"));
            pane.add(directedPanel, c);

            c.gridx = 0;
            c.gridy = 3;
            displacementReestimateBox.setSelected(MHTparameterSet.defaultIsUpdateMotion);
            pane.add(displacementReestimateBox, c);

            this.setBorder(new TitledBorder("Motion settings"));
        }

        public boolean isDirectedMotion()
        {
            return useDirectedMotionBox.isSelected();
        }

        public void setDirectedMotion(boolean isDirectedMotion)
        {
            useDirectedMotionBox.setSelected(isDirectedMotion);
            directedPanel.setEnabled(useDirectedMotionBox.isSelected());
        }

        public double getDisplacementXY()
        {
            if (isDirectedMotion())
                return ((SpinnerNumberModel) directedPanel.spinnerXYVelocity.getModel()).getNumber().doubleValue();
            else
                return ((SpinnerNumberModel) diffusionPanel.spinnerXYDisplacement.getModel()).getNumber().doubleValue();
        }

        public void setDisplacementXY(double d)
        {
            if (isDirectedMotion())
                directedPanel.spinnerXYVelocity.getModel().setValue(d);
            else
                diffusionPanel.spinnerXYDisplacement.getModel().setValue(d);
        }

        public double getDisplacementZ()
        {
            if (isDirectedMotion())
                return ((SpinnerNumberModel) directedPanel.spinnerZVelocity.getModel()).getNumber().doubleValue();
            else
                return ((SpinnerNumberModel) diffusionPanel.spinnerZDisplacement.getModel()).getNumber().doubleValue();
        }

        public void setDisplacementZ(double d)
        {
            if (isDirectedMotion())
                directedPanel.spinnerZVelocity.getModel().setValue(d);
            else
                diffusionPanel.spinnerZDisplacement.getModel().setValue(d);
        }

        public boolean isUpdateMotion()
        {
            return displacementReestimateBox.isSelected();
        }

        public void setUpdateMotion(boolean isUpdateMotion)
        {
            displacementReestimateBox.setSelected(isUpdateMotion);
        }
    }

    public class MultipleMotionPanel extends JPanel
    {
        /**
		 * 
		 */
        private static final long serialVersionUID = 3914304683158900923L;
        final JButton addNewModelButton = new JButton("Add a new model to the mixture");
        final JButton removeModelButton = new JButton("Remove the last model");
        final JPanel modelsPane = new JPanel();
        final JScrollPane scrollPane = new JScrollPane(modelsPane);
        ArrayList<SingleMotionPanel> motionPanels = new ArrayList<SingleMotionPanel>();
        int indexQueue = -1;

        NumberFormat inertiaFormat = NumberFormat.getNumberInstance();
        final protected JFormattedTextField inertiaTF = new JFormattedTextField(inertiaFormat);
        JCheckBox useMostLikelyModelBox = new JCheckBox("Use most likely model");

        public MultipleMotionPanel()
        {
            this.setBorder(new TitledBorder("Multiple motion settings"));
            JPanel pane = this;
            GridBagLayout gbl = new GridBagLayout();
            pane.setLayout(gbl);
            GridBagConstraints c = new GridBagConstraints();
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1.0;
            c.weighty = 0.0;
            c.gridwidth = 1;
            c.insets = new Insets(2, 2, 2, 2);
            c.gridx = 0;
            c.gridy = 0;

            this.add(new JLabel("Inertia for motion model switch:"), c);
            c.gridy++;
            this.add(inertiaTF, c);
            inertiaTF.setText(Double.toString(MHTparameterSet.defaultIMMInertia));
            c.gridy++;
            this.add(useMostLikelyModelBox, c);
            useMostLikelyModelBox.setSelected(MHTparameterSet.defaultUseMostLikelyModel);
            c.gridy++;

            modelsPane.setLayout(new BoxLayout(modelsPane, BoxLayout.PAGE_AXIS));
            SingleMotionPanel motionPanel = new SingleMotionPanel();
            motionPanel.setDirectedMotion(MHTparameterSet.defaultIsDirectedMotion);
            motionPanels.add(motionPanel);
            modelsPane.add(motionPanel);
            indexQueue++;
            motionPanel = new SingleMotionPanel();
            motionPanel.setDirectedMotion(MHTparameterSet.defaultIsDirectedMotion2);
            motionPanels.add(motionPanel);
            modelsPane.add(motionPanel);
            indexQueue++;

            scrollPane.setPreferredSize(new Dimension(200, 400));
            this.add(scrollPane, c);
        }

        public ArrayList<SingleMotionPanel> getSingleMotionPanels()
        {
            return new ArrayList<SingleMotionPanel>(motionPanels);
        }

        public double getInertia()
        {
            try
            {
                return inertiaFormat.parse(inertiaTF.getText()).doubleValue();
            }
            catch (ParseException e)
            {
                new AnnounceFrame("Invalid inertia value. Using default value:" + MHTparameterSet.defaultIMMInertia);
                return MHTparameterSet.defaultIMMInertia;
            }
        }

        public boolean isUseMostLikelyModel()
        {
            return useMostLikelyModelBox.isSelected();
        }
    }

    class KFRandomWalkPanel extends JPanel
    {

        /**
		 * 
		 */
        private static final long serialVersionUID = -4839850887184803243L;

        final protected JSpinner spinnerXYDisplacement = new JSpinner();
        final protected JSpinner spinnerZDisplacement = new JSpinner();

        public KFRandomWalkPanel()
        {
            JPanel pane = this;
            GridBagLayout gbl = new GridBagLayout();
            pane.setLayout(gbl);
            GridBagConstraints c = new GridBagConstraints();
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1.0;
            c.weighty = 0.0;
            c.gridwidth = 1;
            c.insets = new Insets(2, 2, 2, 2);

            spinnerXYDisplacement.setModel(new SpinnerNumberModel(MHTparameterSet.defaultDisplacementXY,
                    new Double(0.5), null, new Double(0.5)));
            JPanel spinnerXYPanel = new JPanel();
            spinnerXYPanel.setLayout(new GridLayout(2, 1, 0, 0));
            spinnerXYPanel.add(new JLabel("Expected displacement length in the x-y plane"));
            spinnerXYPanel.add(spinnerXYDisplacement);
            c.gridx = 0;
            c.gridy = 0;
            pane.add(spinnerXYPanel, c);

            spinnerZDisplacement.setModel(new SpinnerNumberModel(MHTparameterSet.defaultDisplacementZ, new Double(0.5),
                    null, new Double(0.5)));
            JPanel spinnerZPanel = new JPanel();
            spinnerZPanel.setLayout(new GridLayout(2, 1, 0, 0));
            spinnerZPanel.add(new JLabel("Expected displacement length along the z-axis"));
            spinnerZPanel.add(spinnerZDisplacement);
            c.gridx = 0;
            c.gridy = 1;
            pane.add(spinnerZPanel, c);
        }
    }

    class KFDirectedPanel extends JPanel
    {
        /**
		 * 
		 */
        private static final long serialVersionUID = 906775079141737670L;

        final protected JSpinner spinnerXYVelocity = new JSpinner();
        final protected JSpinner spinnerZVelocity = new JSpinner();
        final JLabel labelXYVelocity = new JLabel("Expected displacement length in the x-y plane");
        final JLabel lavelZVelocity = new JLabel("Expected displacement length along the z-axis");

        public KFDirectedPanel()
        {

            JPanel pane = this;
            GridBagLayout gbl = new GridBagLayout();
            pane.setLayout(gbl);
            GridBagConstraints c = new GridBagConstraints();
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1.0;
            c.weighty = 0.0;
            c.gridwidth = 1;
            c.insets = new Insets(2, 2, 2, 2);

            spinnerXYVelocity.setModel(new SpinnerNumberModel(MHTparameterSet.defaultDisplacementXY, new Double(0.5),
                    null, new Double(0.5)));
            JPanel spinnerXYPanel = new JPanel();
            spinnerXYPanel.setLayout(new GridLayout(2, 1, 0, 0));
            spinnerXYPanel.add(labelXYVelocity);
            spinnerXYPanel.add(spinnerXYVelocity);
            c.gridx = 0;
            c.gridy = 0;
            pane.add(spinnerXYPanel, c);

            spinnerZVelocity.setModel(new SpinnerNumberModel(MHTparameterSet.defaultDisplacementZ, new Double(0.5),
                    null, new Double(0.5)));
            JPanel spinnerZPanel = new JPanel();
            spinnerZPanel.setLayout(new GridLayout(2, 1, 0, 0));
            spinnerZPanel.add(lavelZVelocity);
            spinnerZPanel.add(spinnerZVelocity);
            c.gridx = 0;
            c.gridy = 1;
            pane.add(spinnerZPanel, c);
        }

        @Override
        public void setEnabled(boolean enable)
        {
            super.setEnabled(enable);
            spinnerXYVelocity.setEnabled(enable);
            spinnerZVelocity.setEnabled(enable);
            labelXYVelocity.setEnabled(enable);
            lavelZVelocity.setEnabled(enable);
        }
    }

    public boolean isSingleMotion()
    {
        return (filtersBox.getSelectedIndex() == 0);
    }

    public void setSingleMotion(boolean isSingleMotion)
    {
        if (isSingleMotion)
            filtersBox.setSelectedIndex(0);
        else
            filtersBox.setSelectedIndex(1);
        motionCardLayout.show(motionModelPanel, filtersBox.getSelectedItem().toString());
    }

    public void fillParameterSet(MHTparameterSet parameterSet)
    {
        parameterSet.isSingleMotion = isSingleMotion();
        if (isSingleMotion())
        {
            parameterSet.isDirectedMotion = singleMotionPanel.isDirectedMotion();
            parameterSet.displacementXY = singleMotionPanel.getDisplacementXY();
            parameterSet.displacementZ = singleMotionPanel.getDisplacementZ();
            parameterSet.isUpdateMotion = singleMotionPanel.isUpdateMotion();
        }
        else
        {
            parameterSet.isDirectedMotion = multipleMotionPanel.getSingleMotionPanels().get(0).isDirectedMotion();
            parameterSet.displacementXY = multipleMotionPanel.getSingleMotionPanels().get(0).getDisplacementXY();
            parameterSet.displacementZ = multipleMotionPanel.getSingleMotionPanels().get(0).getDisplacementZ();
            parameterSet.isUpdateMotion = multipleMotionPanel.getSingleMotionPanels().get(0).isUpdateMotion();

            parameterSet.isDirectedMotion2 = multipleMotionPanel.getSingleMotionPanels().get(1).isDirectedMotion();
            parameterSet.displacementXY2 = multipleMotionPanel.getSingleMotionPanels().get(1).getDisplacementXY();
            parameterSet.displacementZ2 = multipleMotionPanel.getSingleMotionPanels().get(1).getDisplacementZ();
            parameterSet.isUpdateMotion2 = multipleMotionPanel.getSingleMotionPanels().get(1).isUpdateMotion();

            parameterSet.useMostLikelyModel = multipleMotionPanel.isUseMostLikelyModel();
            parameterSet.immInertia = multipleMotionPanel.getInertia();
        }
    }

    public void setParameters(MHTparameterSet parameterSet)
    {
        setSingleMotion(parameterSet.isSingleMotion);
        if (parameterSet.isSingleMotion)
        {
            singleMotionPanel.setDirectedMotion(parameterSet.isDirectedMotion);
            singleMotionPanel.setDisplacementXY(parameterSet.displacementXY);
            singleMotionPanel.setDisplacementZ(parameterSet.displacementZ);
            singleMotionPanel.setUpdateMotion(parameterSet.isUpdateMotion);
        }
        else
        {
            SingleMotionPanel panel = multipleMotionPanel.getSingleMotionPanels().get(0);
            panel.setDirectedMotion(parameterSet.isDirectedMotion);
            panel.setDisplacementXY(parameterSet.displacementXY);
            panel.setDisplacementZ(parameterSet.displacementZ);
            panel.setUpdateMotion(parameterSet.isUpdateMotion);

            panel = multipleMotionPanel.getSingleMotionPanels().get(1);
            panel.setDirectedMotion(parameterSet.isDirectedMotion2);
            panel.setDisplacementXY(parameterSet.displacementXY2);
            panel.setDisplacementZ(parameterSet.displacementZ2);
            panel.setUpdateMotion(parameterSet.isUpdateMotion2);

            multipleMotionPanel.useMostLikelyModelBox.setSelected(parameterSet.useMostLikelyModel);
            multipleMotionPanel.inertiaTF.setText(Double.toString(parameterSet.immInertia));
        }
    }
}
