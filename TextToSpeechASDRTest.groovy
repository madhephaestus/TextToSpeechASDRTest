import javax.sound.sampled.AudioInputStream

import javafx.application.Platform
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.Label
import javafx.scene.control.Tab
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox

import com.neuronrobotics.bowlerstudio.AudioPlayer
import com.neuronrobotics.bowlerstudio.AudioStatus
import com.neuronrobotics.bowlerstudio.BowlerKernel
import com.neuronrobotics.bowlerstudio.BowlerStudio
import com.neuronrobotics.bowlerstudio.BowlerStudioController
import com.neuronrobotics.bowlerstudio.IAudioProcessingLambda
import com.neuronrobotics.bowlerstudio.ISpeakingProgress
import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager
import com.neuronrobotics.bowlerstudio.creature.MobileBaseLoader
import com.neuronrobotics.sdk.addons.kinematics.AbstractLink
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics
import com.neuronrobotics.sdk.addons.kinematics.MobileBase
import com.neuronrobotics.sdk.common.DeviceManager
import com.neuronrobotics.sdk.util.ThreadUtil


import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Series;
import javax.speech.Engine ;


boolean regen=false;
MobileBase base=DeviceManager.getSpecificDevice( "Standard6dof",{
	//If the device does not exist, prompt for the connection

	MobileBase m = MobileBaseLoader.fromGit(
			"https://github.com/Halloween2020TheChild/GroguMechanicsCad.git",
			"hephaestus.xml"
			)
	if(m==null)
		throw new RuntimeException("Arm failed to assemble itself")
	println "Connecting new device robot arm "+m
	return m
})
MobileBaseCadManager get = MobileBaseCadManager.get( base)
ThreadUtil.wait(200)
boolean wasCOnfig = get.configMode
if(wasCOnfig) {
	while(get.getProcesIndictor().get()<1){
		println "Waiting for Config DISPLA to get to 1:"+get.getProcesIndictor().get()
		ThreadUtil.wait(1000)
	}
	get.setConfigurationViewerMode(false)
	get.generateCad()
	ThreadUtil.wait(200)
}
while(get.getProcesIndictor().get()<1){
	println "Waiting for cad to get to 1:"+get.getProcesIndictor().get()
	ThreadUtil.wait(1000)
}

DHParameterKinematics spine = base.getAllDHChains().get(0);
MobileBase head = spine.getSlaveMobileBase(5)
AbstractLink mouth =head.getAllDHChains().get(0).getAbstractLink(0)
AudioPlayer.setIntegralDepth(20)
AudioPlayer.setThreshhold(0.01)
AudioPlayer.setLowerThreshhold(0.005)
AudioPlayer.setIntegralGain(1);
AudioPlayer.setDerivitiveGain(1);
double globalAmp=0;
double globalCurrentRoll=0;
double globalCurrentDeriv=0;
double globalCurrentCalculated=0;
boolean update=false;

AudioPlayer.setLambda( new IAudioProcessingLambda() {
		// code reference from the face application https://github.com/adafruit/Adafruit_Learning_System_Guides/blob/main/AdaVoice/adavoice_face/adavoice_face.ino
		int xfadeDistance=16;
		double [] samples = new double[xfadeDistance];
		int xfadeIndex=0;
		boolean stare=true;
		@Override
		public AudioStatus update(AudioStatus currentStatus, double amplitudeUnitVector, double currentRollingAverage,
				double currentDerivitiveTerm, double percent) {
			if(stare) {
				stare=false;
				for(int i=0;i<xfadeDistance;i++) {
					samples[i]=currentRollingAverage;
				}
			}
			double index=samples[xfadeIndex];
			samples[xfadeIndex]=currentRollingAverage;
			xfadeIndex++;
			if(xfadeIndex==xfadeDistance) {
				xfadeIndex=0;
			}
			double val = (currentRollingAverage+index)/2*currentDerivitiveTerm;
			switch(currentStatus) {
				case AudioStatus.B_KST_SOUNDS:
					if(val>AudioPlayer.getThreshhold()) {
						currentStatus=AudioStatus.D_AA_SOUNDS;
					}
					break;
				case AudioStatus.G_F_V_SOUNDS:
					if(val<AudioPlayer.getLowerThreshhold()) {
						currentStatus=AudioStatus.X_NO_SOUND;
					}
					break;
				case AudioStatus.X_NO_SOUND:
					if(val>AudioPlayer.getThreshhold()) {
						currentStatus=AudioStatus.B_KST_SOUNDS;
					}
					break;
				case AudioStatus.D_AA_SOUNDS:
					if(val<AudioPlayer.getLowerThreshhold()) {
						currentStatus=AudioStatus.G_F_V_SOUNDS;
					}
					break;
				default:
					break;
			}
			return currentStatus;
		}

		@Override
		public AudioInputStream startProcessing(AudioInputStream ais) {
			stare=true;
			return ais;
		}
	});
public class GraphManager {
	private ArrayList<XYChart.Series> pidGraphSeries=new ArrayList<>();
	private LineChart<Double, Double> pidGraph;
	private double start = ((double) System.currentTimeMillis()) / 1000.0;
	private long lastPos;
	private long lastSet;
	private long lastHw;
	private long lastVal;
	//	private HashMap<Integer,ArrayList<Double>> posExp = new HashMap<>();
	//	private HashMap<Integer,ArrayList<Double>> setExp = new HashMap<>();
	//	private HashMap<Integer,ArrayList<Double>> hwExp = new HashMap<>();
	//	private HashMap<Integer,ArrayList<Double>> timeExp = new HashMap<>();
	private int currentIndex=0;
	private int numPid=0;
	public GraphManager(LineChart<Double, Double> g, int num ) {
		pidGraph=g;
		numPid=num;
		for (int i = 0; i < numPid; i++) {
			Series e = new XYChart.Series();

			pidGraphSeries.add(i, e);
			pidGraph.getData().add(e);
			//			posExp.put(i,new ArrayList<>());
			//			setExp.put(i,new ArrayList<>());
			//			hwExp.put(i,new ArrayList<>());
			//			timeExp.put(i,new ArrayList<>());
		}
		pidGraph.getXAxis().autoRangingProperty().set(true);

	}

	@SuppressWarnings("unchecked")
	public  void updateGraph(double pos, double set, double hw, double val) {
		if (pidGraphSeries.size() == 0)
			return;
		double now = ((double) System.currentTimeMillis()) / 1000.0 - start;
		long thispos = (long) (pos*100.0);
		long thisSet = (long) (set*100.0);
		long thisHw  = (long) (hw*100.0);
		long thisVal = (long) (val*100.0);
		if (thispos != lastPos || thisSet != lastSet || thisHw!=lastHw) {
			pidGraphSeries.get(0).getData().add(new XYChart.Data(now - 0.0001, pos));
			pidGraphSeries.get(1).getData().add(new XYChart.Data(now - 0.0001, set));
			pidGraphSeries.get(2).getData().add(new XYChart.Data(now - 0.0001, hw));
			pidGraphSeries.get(3).getData().add(new XYChart.Data(now - 0.0001, val));
			lastSet = thisSet;
			lastPos = thispos;
			lastHw=thisHw;
			lastVal=thisVal;
			pidGraphSeries.get(0).getData().add(new XYChart.Data(now, pos));
			pidGraphSeries.get(1).getData().add(new XYChart.Data(now, set));
			pidGraphSeries.get(2).getData().add(new XYChart.Data(now , hw));
			pidGraphSeries.get(3).getData().add(new XYChart.Data(now , val));
			//			posExp.get(currentIndex).add(pos);
			//			setExp.get(currentIndex).add(set);
			//			hwExp.get(currentIndex).add(hw);
			//			timeExp.get(currentIndex).add(now);
			//			if(posExp.get(currentIndex).size()>5000) {
			//				posExp.get(currentIndex).remove(0);
			//				setExp.get(currentIndex).remove(0);
			//				hwExp.get(currentIndex).remove(0);
			//				timeExp.get(currentIndex).remove(0);
			//			}
		}
//		for (Series s : pidGraphSeries) {
//			while (s.getData().size() > 2000) {
//				s.getData().remove(0);
//			}
//		}
	}
	public void clearGraph(int currentIndex) {
		for (Series s : pidGraphSeries) {
			s.getData().clear();

		}
		this.currentIndex=currentIndex;
	}
}

ISpeakingProgress sp ={double percent,AudioStatus status->

	boolean isMouthOpen = status.isOpen()
	mouth.setTargetEngineeringUnits(isMouthOpen?-10.0:0);
	mouth.flush(0);

}
Tab t= new Tab();
final NumberAxis xAxis = new NumberAxis();
final NumberAxis yAxis = new NumberAxis();
LineChart<Double, Double> pidGraph = new LineChart<Double,Double>(xAxis,yAxis);

GraphManager m=new GraphManager(pidGraph,4);

VBox content = new VBox();
content.getChildren().add(new Label("Audio Processing Graph"));
content.getChildren().add(pidGraph);
t.setContent(content)
BowlerStudioController.addObject(t, null);

boolean run=true;
new Thread({

	while(run) {
		Thread.sleep(16)
		if(update) {
			BowlerStudio.runLater({
				m.updateGraph( globalAmp+1.0,
						globalCurrentRoll+2.0,
						globalCurrentDeriv,
						globalCurrentCalculated+3.0)
				update=false;
			})
		}
	}
}).start()

double i=805
try {
	BowlerKernel.speak("A test phrase... a pause...a quick, brown fox jumpes over the lazy dog.", 100, 0, i, 1.0, 1.0,sp)
}catch(Throwable tr) {
	BowlerStudio.printStackTrace(tr)
}
run=false;
Thread.sleep(100)
mouth.setTargetEngineeringUnits(0);


