package plugins.nchenouard.particletracking.legacytracker.gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.text.DecimalFormat;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;


/** Panel to configure the spatial covariance of predictors
 * 
 * Part of the Spot Tracking plugin for ICY: http://icy.bioimageanalysis.org/plugin/Spot_Tracking
 * 
 * @author Nicolas Chenouard (nicolas.chenouard@gmail.com).
 * @version 3.1
 * @date 2013-11-13
 * @license gpl v3.0
*/

public class FilterCovariancesPanel extends JPanel
{	
	private static final long serialVersionUID = -6540605877193830658L;
	
	private DecimalFormat f1 = new DecimalFormat("000");	
	private double[] trackCov  = { 3 ,3 , 1, 150 , 150  }; 
	private JSlider xSens = new JSlider(JSlider.HORIZONTAL, 0, 60,(int)Math.round(trackCov[0]));
	private JSlider ySens = new JSlider(JSlider.HORIZONTAL,0, 60,(int)Math.round(trackCov[1]));
	private JSlider zSens = new JSlider(JSlider.HORIZONTAL,0, 60,(int)Math.round(trackCov[2]));
	private JLabel xLab =new JLabel();
	private JLabel yLab =new JLabel();
	private JLabel zLab =new JLabel();
	private JLabel aLab =new JLabel();
	private JLabel iLab =new JLabel();
	private JCheckBox icheckB = new JCheckBox("Intensity");
	private JCheckBox acheckB = new JCheckBox("Area");

	public void setDefaultValues()
	{
		xSens.setValue((int)Math.round(trackCov[0]));
		ySens.setValue((int)Math.round(trackCov[1]));
		zSens.setValue((int)Math.round(trackCov[2]));
		xLab.setText(f1.format(new Double(trackCov[0]).doubleValue()));
		yLab.setText(f1.format(new Double(trackCov[1]).doubleValue()));
		zLab.setText(f1.format(new Double(trackCov[2]).doubleValue()));
		aLab.setText(f1.format(new Double(trackCov[3]).doubleValue()));
		iLab.setText(f1.format(new Double(trackCov[4]).doubleValue()));
		acheckB.setSelected(false);
		icheckB.setSelected(false);
	}

	public FilterCovariancesPanel()
	{
		GridBagLayout mainLayout = new GridBagLayout();
		this.setLayout(mainLayout);

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridheight = 1;
		constraints.gridwidth = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		
		xSens.addChangeListener(new ChangeListener(){
			public void stateChanged(ChangeEvent arg0) {
				xLab.setText(f1.format(xSens.getValue()));
			}});
		ySens.addChangeListener(new ChangeListener(){
			public void stateChanged(ChangeEvent arg0) {
				yLab.setText(f1.format(ySens.getValue()));
			}});
		zSens.addChangeListener(new ChangeListener(){
			public void stateChanged(ChangeEvent arg0) {
				zLab.setText(f1.format(zSens.getValue()));
			}});

		JPanel jpx = new JPanel();
		jpx.setLayout(new BoxLayout(jpx, BoxLayout.X_AXIS));
		jpx.add(new JLabel("x"));
		jpx.add(xSens);
		jpx.add(xLab);

		JPanel jpy= new JPanel();
		jpy.setLayout(new BoxLayout(jpy, BoxLayout.X_AXIS));
		jpy.add(new JLabel("y"));
		jpy.add(ySens);
		jpy.add(yLab);

		JPanel jpz= new JPanel();
		jpz.setLayout(new BoxLayout(jpz, BoxLayout.X_AXIS));
		jpz.add(new JLabel("z"));
		jpz.add(zSens);
		jpz.add(zLab);

		this.add(jpx, constraints);
		constraints.gridy++;
		this.add(jpy, constraints);
		constraints.gridy++;
		this.add(jpz, constraints);
		constraints.gridy++;
	}
	
	public boolean isAreaSelected()
	{
		return acheckB.isSelected();
	}
	public boolean isIntensitySelected()
	{
		return icheckB.isSelected();
	}

	public double getTrackingCovX()
	{
		return xSens.getValue();
	}

	public double getTrackingCovY()
	{
		return ySens.getValue();
	}

	public double getTrackingCovZ()
	{
		return zSens.getValue();
	}
}
