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

ScriptingEngine.gitScriptRun("https://github.com/madhephaestus/TextToSpeechASDRTest.git", "VoskLipSync.groovy")

ISpeakingProgress sp ={double percent,AudioStatus status->
	//println percent+" " +status
	double isMouthOpen = status.mouthOpenVector()
	mouth.setTargetEngineeringUnits(isMouthOpen*-20.0);
	mouth.flush(0);

}




double i=905
try {
	BowlerKernel.speak("A test phrase... a pause...a quick, brown fox jumpes over the lazy dog. Big bat, batman wow father", 100, 0, i, 1.0, 1.0,sp)
	BowlerKernel.speak("This is a secong phrase", 100, 0, i, 1.0, 1.0,sp)
	
}catch(Throwable tr) {
	BowlerStudio.printStackTrace(tr)
}
Thread.sleep(100)
mouth.setTargetEngineeringUnits(0);


