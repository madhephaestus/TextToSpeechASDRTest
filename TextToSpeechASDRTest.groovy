import com.neuronrobotics.bowlerstudio.AudioStatus
import com.neuronrobotics.bowlerstudio.BowlerKernel
import com.neuronrobotics.bowlerstudio.BowlerStudio
import com.neuronrobotics.bowlerstudio.ISpeakingProgress
import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager
import com.neuronrobotics.bowlerstudio.creature.MobileBaseLoader
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine
import com.neuronrobotics.sdk.addons.kinematics.AbstractLink
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics
import com.neuronrobotics.sdk.addons.kinematics.MobileBase
import com.neuronrobotics.sdk.common.DeviceManager
import com.neuronrobotics.sdk.util.ThreadUtil

import javafx.scene.image.Image

HashMap<AudioStatus,Image> images = new HashMap<>()

for(AudioStatus s:EnumSet.allOf(AudioStatus.class)) {
	File f = new File(ScriptingEngine.fileFromGistID("https://github.com/madhephaestus/TextToSpeechASDRTest.git", "img/lisa-"+s.parsed+".png"))
	Image image = new Image(f.getAbsolutePath());
	images.put(s, image)
}

ISpeakingProgress sp ={double percent,AudioStatus status->
	println percent+" " +status
	
}

println images


double i=1
try {
	BowlerKernel.speak("The mighty Zoltar sees your future! You have much to look forward to!", 100, 0, i, 1.0, 1.0,sp)
}catch(Throwable tr) {
	BowlerStudio.printStackTrace(tr)
}


