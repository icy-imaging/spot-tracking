package plugins.nchenouard.particletracking;

import icy.main.Icy;
import icy.swimmingPool.SwimmingObject;
import icy.swimmingPool.SwimmingPoolEvent;
import icy.swimmingPool.SwimmingPoolListener;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;

import plugins.nchenouard.spot.DetectionResult;

/**
 * The detection chooser pick in the swimming pool the object corresponding to DetectionResult
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com)
 * @author Fabrice de Chaumont, Stephane Dallongeville and Nicolas Chenouard<br>
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
 */
public class DetectionChooser extends JComboBox implements SwimmingPoolListener
{
    public interface DetectionResultChooserListener
    {
        public void DetectionResultChanged(DetectionResult detectionResult);
    }

    private class DetectionComboModel extends DefaultComboBoxModel
    {
        /**
		 * 
		 */
        private static final long serialVersionUID = 3064234412504253556L;

        /**
         * items list
         */
        final ArrayList<DetectionResult> detectionResults;

        /**
         * @param sequences
         */
        public DetectionComboModel(ArrayList<DetectionResult> detectionResults)
        {
            super();

            this.detectionResults = detectionResults;
        }

        @Override
        public DetectionResult getElementAt(int index)
        {
            return detectionResults.get(index);
        }

        @Override
        public int getSize()
        {
            return detectionResults.size();
        }
    }

    private static final long serialVersionUID = -6108163762809540675L;

    /**
     * listeners
     */
    private final ArrayList<DetectionResultChooserListener> listeners;

    /**
     * internals
     */
    private DetectionResult previousDetectionResult;

    public DetectionChooser()
    {
        super();
        setRenderer(new ListCellRenderer()
        {
            // @Override
            // public Component getListCellRendererComponent(JList<DetectionResult> list, DetectionResult value, int
            // index, boolean isSelected,
            // boolean cellHasFocus)
            // {
            // if (value == null)
            // return new JLabel(" no detection result - Please use the Spot detector to create a detection set.");
            //
            // if (value instanceof DetectionResult )
            // return new JLabel(StringUtil.limit( value.toString() , 50 ));
            //
            // return new JLabel("");
            // }

            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
                    boolean cellHasFocus)
            {
                if (value == null)
                    return new JLabel("Select detection results here");

                if (value instanceof DetectionResult)
                    // return new JLabel(StringUtil.limit( value.toString() , 50 ));
                    return new JLabel(value.toString());
                return new JLabel("");
            }
        });

        Icy.getMainInterface().getSwimmingPool().addListener(this);

        addActionListener(this);

        // default
        listeners = new ArrayList<DetectionResultChooserListener>();

        previousDetectionResult = null;
        setSelectedItem(null);

        // refresh list
        refreshDetectionResultList();

        // fix height
        // ComponentUtil.setFixedHeight(this, 26);
    }

    /**
     * @return current detectionResult selected in combo. null if no sequence is selected or if the
     *         sequence do not exists anymore.
     */
    public DetectionResult getSelectedDetectionResult()
    {
        return (DetectionResult) getSelectedItem();
    }

    /**
     * @param detectionResult
     *        The detectionResult to select in the combo box.
     */
    public void setDetectionResultSelected(DetectionResult detectionResult)
    {
        if (detectionResult != getSelectedDetectionResult())
        {
            setSelectedItem(detectionResult);
        }
    }

    void refreshDetectionResultList()
    {
        // save old selection
        final Object oldSelected = getSelectedItem();
        // rebuild model
        setModel(new DetectionComboModel(getDetectionResultList()));
        // restore selection
        setSelectedItem(oldSelected);
    }

    private ArrayList<DetectionResult> getDetectionResultList()
    {

        ArrayList<DetectionResult> detectionResultList = new ArrayList<DetectionResult>();

        ArrayList<SwimmingObject> objects = Icy.getMainInterface().getSwimmingPool().getObjects();

        for (SwimmingObject so : objects)
        {
            System.out.println(so.getObject());
            Object o = so.getObject();
            System.out.println(o instanceof DetectionResult);
            if (so.getObject() instanceof DetectionResult)
            {
                // System.out.println("Object added: " + so.getObject() );
                detectionResultList.add((DetectionResult) so.getObject());
            }
        }

        return detectionResultList;

    }

    /** called when the selection has changed */
    private void detectionResultChanged(DetectionResult detectionResult)
    {
        fireDetectionResultChanged(detectionResult);
    }

    private void fireDetectionResultChanged(DetectionResult detectionResult)
    {
        final ArrayList<DetectionResultChooserListener> listenersCopy = getListeners();

        for (DetectionResultChooserListener listener : listenersCopy)
            listener.DetectionResultChanged(detectionResult);
    }

    public ArrayList<DetectionResultChooserListener> getListeners()
    {
        return new ArrayList<DetectionResultChooserListener>(listeners);
    }

    public void addListener(DetectionResultChooserListener listener)
    {
        if (!listeners.contains(listener))
            listeners.add(listener);
    }

    public void removeListener(DetectionResultChooserListener listener)
    {
        listeners.remove(listener);
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        final DetectionResult detectionResult = getSelectedDetectionResult();

        if (previousDetectionResult != detectionResult)
        {
            previousDetectionResult = detectionResult;
            detectionResultChanged(detectionResult);
        }
    }

    @Override
    public void swimmingPoolChangeEvent(final SwimmingPoolEvent event)
    {

        // Thread.in
        SwingUtilities.invokeLater(

        new Runnable()
        {

            @Override
            public void run()
            {

                refreshDetectionResultList();

                // Select the last entry computed
                if (event.getResult().getObject() instanceof DetectionResult)
                {
                    setSelectedItem(event.getResult().getObject());
                }
            }
        }

        );
    }

}