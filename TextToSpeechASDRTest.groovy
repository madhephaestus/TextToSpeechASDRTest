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
public double mouthOpenVector(AudioStatus s) {
	switch(s) {
	case AudioStatus.B_KST_SOUNDS:
		return 0.3;
	case AudioStatus.C_EH_AE_SOUNDS:
		return 0.6;
	case AudioStatus.D_AA_SOUNDS:
		return 1;
	case AudioStatus.E_AO_ER_SOUNDS:
		return 0.6;
	case AudioStatus.F_UW_OW_W_SOUNDS:
		return 0.2;
	case AudioStatus.G_F_V_SOUNDS:
		return 0.1;
	case AudioStatus.H_L_SOUNDS:
		return 0.9;
	case AudioStatus.X_NO_SOUND:
	case AudioStatus.A_PBM_SOUNDS:
	default:
		break;
	}
	return 0;
}
ISpeakingProgress sp ={double percent,AudioStatus status->
	//println percent+" " +status
	double isMouthOpen = mouthOpenVector(status)
	mouth.setTargetEngineeringUnits(isMouthOpen*-20.0);
	mouth.flush(0);

}




double i=905
try {
	BowlerKernel.speak("Don't worry you'll be listening, for technocopia, to the story of the Zoltar machine", 100, 0, i, 1.0, 1.0,sp)
	BowlerKernel.speak("This is a secong phrase words to say with my mouth", 100, 0, i, 1.0, 1.0,sp)
	
}catch(Throwable tr) {
	BowlerStudio.printStackTrace(tr)
}
Thread.sleep(100)
mouth.setTargetEngineeringUnits(0);


