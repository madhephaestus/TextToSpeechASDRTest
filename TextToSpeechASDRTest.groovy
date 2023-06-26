
import com.neuronrobotics.bowlerstudio.AudioPlayer
import com.neuronrobotics.bowlerstudio.AudioStatus
import com.neuronrobotics.bowlerstudio.BowlerKernel
import com.neuronrobotics.bowlerstudio.BowlerStudio
import com.neuronrobotics.bowlerstudio.BowlerStudioController
import com.neuronrobotics.bowlerstudio.ISpeakingProgress
import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager
import com.neuronrobotics.bowlerstudio.creature.MobileBaseLoader
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine
import com.neuronrobotics.sdk.addons.kinematics.AbstractLink
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics
import com.neuronrobotics.sdk.addons.kinematics.MobileBase
import com.neuronrobotics.sdk.common.DeviceManager
import com.neuronrobotics.sdk.util.ThreadUtil

import javafx.scene.control.Tab
import javafx.scene.image.Image
import javafx.application.Platform
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

HashMap<AudioStatus,Image> images = new HashMap<>()
String url = "https://github.com/madhephaestus/TextToSpeechASDRTest.git"
for(AudioStatus s:EnumSet.allOf(AudioStatus.class)) {
	File f = new File(ScriptingEngine.getRepositoryCloneDirectory(url).getAbsolutePath()+ "/img/lisa-"+s.parsed+".png")
	println "Loading "+f.getAbsolutePath()
	Image image = new Image(new FileInputStream(f.getAbsolutePath()));
	images.put(s, image)
}

AudioPlayer.setLambda (com.neuronrobotics.bowlerstudio.lipsync.VoskLipSync.get());

ImageView imageView = new ImageView(images.get(AudioStatus.X_NO_SOUND));
laststatus=null
ISpeakingProgress sp ={double percent,AudioStatus status->
	if(status!=laststatus) {
		println percent+" " +status
		laststatus=status;
	}
	Platform.runLater({
		imageView.setImage(images.get(status))
	})
}

Tab t = new Tab()
t.setContent(imageView)
BowlerStudioController.addObject(t, null)
Thread.sleep(1000)

double i=800
try {
	BowlerKernel.speak("The mighty Zoltar sees your future! You have much to look forward to!", 100, 0, i, 1.0, 1.0,sp)
}catch(Throwable tr) {
	BowlerStudio.printStackTrace(tr)
}


