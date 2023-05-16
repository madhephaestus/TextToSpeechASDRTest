import javafx.application.Platform
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import com.neuronrobotics.bowlerstudio.AudioPlayer
import com.neuronrobotics.bowlerstudio.AudioStatus
import com.neuronrobotics.bowlerstudio.IAudioProcessingLambda
import com.neuronrobotics.bowlerstudio.ISpeakingProgress
import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager
import com.neuronrobotics.bowlerstudio.creature.MobileBaseLoader
import com.neuronrobotics.sdk.addons.kinematics.AbstractLink
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics
import com.neuronrobotics.sdk.addons.kinematics.MobileBase
import com.neuronrobotics.sdk.common.DeviceManager


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

AudioPlayer.setThreshhold(0.01)
AudioPlayer.setLowerThreshhold(0.001)
AudioPlayer.setIntegralGain(1);
AudioPlayer.setDerivitiveGain(1);
AudioPlayer.setLambda( new IAudioProcessingLambda() {
	
	@Override
	public AudioStatus update(AudioStatus currentStatus, double amplitudeUnitVector, double currentRollingAverage,
			double currentDerivitiveTerm) {
			
		switch(currentStatus) {
		case AudioStatus.attack:
			if(amplitudeUnitVector>AudioPlayer.getThreshhold()) {
				currentStatus=AudioStatus.sustain;
			}
			break;
		case AudioStatus.decay:
			if(amplitudeUnitVector<AudioPlayer.getLowerThreshhold()) {
				currentStatus=AudioStatus.release;
			}
			break;
		case AudioStatus.release:
			if(amplitudeUnitVector>AudioPlayer.getThreshhold()) {
				currentStatus=AudioStatus.attack;
			}
			break;
		case AudioStatus.sustain:
			if(amplitudeUnitVector<AudioPlayer.getLowerThreshhold()) {
				currentStatus=AudioStatus.decay;
			}
			break;
		default:
			break;
		}
		return currentStatus;
	}
	
	@Override
	public void startProcessing() {

	}
});
ISpeakingProgress sp ={double percent,AudioStatus status->
	println "Progress: "+percent+"% Status "+status+" "
	if(status==AudioStatus.release||status==AudioStatus.sustain)
		return
	boolean isMouthOpen = (status==AudioStatus.attack)
	mouth.setTargetEngineeringUnits(isMouthOpen?-20.0:0);
	mouth.flush(0);
	
}
BowlerKernel.speak("A test phrase... a pause...a quick, brown fox jumpes over the lazy dog.", 100, 0, 201, 1.0, 1.0,sp)


mouth.setTargetEngineeringUnits(0);

