import javafx.application.Platform
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import com.neuronrobotics.bowlerstudio.AudioPlayer
import com.neuronrobotics.bowlerstudio.AudioStatus
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

ISpeakingProgress sp ={double percent,AudioStatus status->
	println "Progress: "+percent+"% Status "+status+" "
	if(gpt.a!=null) {
		Platform.runLater( {
			a.setContentText((status==AudioStatus.attack)?"0":"-");
		});
	}
}
BowlerKernel.speak("A test phrase... a pause...a quick, brown, fox jumpes over the lazy dog.", 100, 0, 201, 1.0, 1.0,sp)



