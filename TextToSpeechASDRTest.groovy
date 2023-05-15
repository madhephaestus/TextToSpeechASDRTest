import javafx.application.Platform
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import com.neuronrobotics.bowlerstudio.AudioPlayer
import com.neuronrobotics.bowlerstudio.AudioStatus
import com.neuronrobotics.bowlerstudio.IAudioProcessingLambda
import com.neuronrobotics.bowlerstudio.ISpeakingProgress

// code here
Alert a=null;
Platform.runLater( {
	Alert alert = new Alert(AlertType.INFORMATION);
	a = alert;
	alert.setTitle("Mouth Motion Simulator");
	alert.setHeaderText("");
	alert.setContentText("Loading...");
	alert.showAndWait();
});

while(a==null)
	Thread.sleep(100);
AudioPlayer.setThreshhold(600/65535.0)
AudioPlayer.setLowerThreshhold(100/65535.0)
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
	if(a!=null) {
		Platform.runLater( {
			boolean isMouthOpen = (status==AudioStatus.attack||status==AudioStatus.sustain)
			String local =isMouthOpen?"0":"-"
			a.setContentText(local);
		});
	}
}
BowlerKernel.speak("A test phrase... a pause...a quick, brown fox jumpes over the lazy dog.", 100, 0, 201, 1.0, 1.0,sp)
Platform.runLater( {a.close();})



