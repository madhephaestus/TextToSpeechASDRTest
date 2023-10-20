
import java.lang.reflect.Type

import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine
import javax.sound.sampled.UnsupportedAudioFileException
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.sound.sampled.AudioInputStream;

import marytts.LocalMaryInterface;
import marytts.MaryInterface;
import marytts.exceptions.MaryConfigurationException;
import marytts.exceptions.SynthesisException;
import marytts.modules.synthesis.Voice;
import marytts.signalproc.effects.AudioEffect;
import marytts.signalproc.effects.AudioEffects;
import org.vosk.Model
import org.vosk.Recognizer

import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.neuronrobotics.bowlerstudio.AudioPlayer
import com.neuronrobotics.bowlerstudio.AudioStatus
import com.neuronrobotics.bowlerstudio.BowlerKernel
import com.neuronrobotics.bowlerstudio.BowlerStudio
import com.neuronrobotics.bowlerstudio.BowlerStudioController
import com.neuronrobotics.bowlerstudio.IAudioProcessingLambda
import com.neuronrobotics.bowlerstudio.ISpeakingProgress
import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager
import com.neuronrobotics.bowlerstudio.creature.MobileBaseLoader
import com.neuronrobotics.bowlerstudio.lipsync.PhoneticDictionary
import com.neuronrobotics.bowlerstudio.lipsync.TimeCodedViseme
import com.neuronrobotics.bowlerstudio.lipsync.VoskLipSync
import com.neuronrobotics.bowlerstudio.lipsync.VoskLipSync.VoskPartial
import com.neuronrobotics.bowlerstudio.lipsync.VoskLipSync.VoskResultWord
import com.neuronrobotics.bowlerstudio.lipsync.VoskLipSync.VoskResultl
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine
import com.neuronrobotics.sdk.addons.kinematics.AbstractLink
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics
import com.neuronrobotics.sdk.addons.kinematics.MobileBase
import com.neuronrobotics.sdk.common.DeviceManager
import com.neuronrobotics.sdk.common.IDeviceProvider
import com.neuronrobotics.sdk.util.ThreadUtil

import javafx.scene.control.Tab
import javafx.scene.image.Image
import javafx.application.Platform
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import marytts.MaryInterface
import net.lingala.zip4j.ZipFile


public class VoskLipSyncLocal implements IAudioProcessingLambda {

	class VoskResultWord {
		double conf;
		double end;
		double start;
		String word;

		public String toString() {
			return "\n'" + word + "' \n\tstarts at " + start + " ends at " + end + " confidence " + conf;
		}
	}

	class VoskPartial {
		String partial;
		List<VoskResultWord> partial_result;
	}

	class VoskResultl {
		String text;
		List<VoskResultWord> result;
	}

	Type partailType = new TypeToken<VoskPartial>() {
	}.getType();
	Type resultType = new TypeToken<VoskResultl>() {
	}.getType();

	static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private  Model model;
	private  String modelName;
	private  PhoneticDictionary dict;
	private  AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 60000, 16, 2, 4, 44100, false);
	VoskLipSync sync;

	public VoskLipSyncLocal() {
		sync = VoskLipSync.get();
		model=sync.model;
		dict=sync.dict;
		
		
	}
	/**
	 * @return the modelName
	 */
	public  String getModelName() {
		return sync.getModelName();
	}



	int numBytesRead = 0;
	int CHUNK_SIZE = 4096;
	byte[] abData = new byte[CHUNK_SIZE];
	ArrayList<TimeCodedViseme> timeCodedVisemes = null;
	ArrayList<TimeCodedViseme> timeCodedVisemesCache = new ArrayList<TimeCodedViseme>();
	int words = 0;
	private double positionInTrack;

	private AudioStatus toStatus(String phoneme) {
		AudioStatus s = AudioStatus.getFromPhoneme(phoneme);
		if (s != null)
			return s;
		// println "Unknown phoneme "+phoneme
		return AudioStatus.X_NO_SOUND;
	}

	private void addWord(VoskResultWord word, long len) {

		double secLen = ((double) len) / 1000.0;
        //println word.word;
		String w = word.word;
		if (w == null)
			return;

		double wordStart = word.start;
		double wordEnd = word.end;
		double wordLen = wordEnd - wordStart;
        //println w + ", " + wordStart + ", " + phonemes;
		ArrayList<String> phonemes = dict.find(w);
		if (phonemes == null) {
			// println "\n\n unknown word "+w+"\n\n"
			return;
		}

		double phonemeLength = wordLen / phonemes.size();
        
        Random rand = new Random();
        double timeLeadLag = 0 //-(1/24.0/2048)

		//@finn this is where to adjust the lead/lag of the lip sync with the audio playback
        //mtc -- this is where we can fuck with sequencing and add transition frames.  the transition's probably going to require some sort of javaFX bullshit but we'll see.
		for (int i = 0; i < phonemes.size(); i++) {
			String phoneme = phonemes.get(i);
			AudioStatus stat = toStatus(phoneme);            
			double myStart = Math.max(wordStart + phonemeLength * ((double) i)+timeLeadLag ,  0);
			double myEnd = wordStart + phonemeLength * ((double) (i + 1))+timeLeadLag;
            double segLen = myEnd - myStart;
			TimeCodedViseme tc = new TimeCodedViseme(stat, myStart, myEnd, secLen);
            
            //adds a transitional silent viseme when a silence longer than 1/100 of a second is detected
			if (timeCodedVisemes.size() > 0) {
				TimeCodedViseme tcLast = timeCodedVisemes.get(timeCodedVisemes.size() - 1);
				if (myStart - tcLast.end > 0.03) {
                    
                    // for longer pauses, transition through partially open mouth to close
                    float siLength = myStart - tcLast.end;
                    float hLength = siLength / 3.0;
                    float mouthClosedTime = myStart - hLength;
                    
					TimeCodedViseme tcSilentK = new TimeCodedViseme(AudioStatus.K_user_define, tcLast.end, mouthClosedTime, secLen);
					TimeCodedViseme tcSilentX = new TimeCodedViseme(AudioStatus.X_NO_SOUND, mouthClosedTime, myStart, secLen);
                    
                    //println "ln 297";
					add(tcSilentK);
					add(tcSilentX);
                } else if (myStart - tcLast.end > 0) {
					// short transition to partially open mouth
					TimeCodedViseme tcSilent = new TimeCodedViseme(AudioStatus.H_L_SOUNDS, tcLast.end, myStart, secLen);
					add(tcSilent);                    
                }
			}
            
            //handles situations at the end of words
            add(tc);                 
		}
        

		// println "Word "+w+" starts at "+wordStart+" ends at "+wordEnd+" each phoneme
		// length "+phonemeLength+" "+phonemes+" "+timeCodedVisemes

	}

	private void add(TimeCodedViseme v) {
		// println "Adding "+ v
		timeCodedVisemes.add(v);
		timeCodedVisemesCache.add(v);

	}

	private void processWords(List<VoskResultWord> wordList, long len) {
		if (wordList == null)
			return;

		for (; words < wordList.size(); words++) {
			VoskResultWord word = wordList.get(words);
			addWord(word, len);
		}

	}
    
    private void printTCV() {
        for (int i = 0; i < timeCodedVisemes.size(); i++ ) {
            TimeCodedViseme tcv = timeCodedVisemes.get(i);
            println i + ', "' + tcv.status + '", ' + tcv.start + ', ' + tcv.end + ', ' + tcv.total;
        }
    }
    
	public void processRaw(File f, String ttsLocation) throws UnsupportedAudioFileException, IOException {

		words = 0;
		positionInTrack = 0;
		AudioInputStream getAudioInputStream = AudioSystem.getAudioInputStream(f);
		long durationInMillis = (long) (1000 * getAudioInputStream.getFrameLength()
				/ getAudioInputStream.getFormat().getFrameRate());
		long start = System.currentTimeMillis();
		timeCodedVisemesCache.clear();
		Thread t = new Thread( {
			try {

				double secLen = ((double) durationInMillis) / 1000.0;
				AudioInputStream ais = AudioSystem.getAudioInputStream(format, getAudioInputStream);
				Recognizer recognizer = new Recognizer(model, 120000);
				recognizer.setWords(true);
				recognizer.setPartialWords(true);
				numBytesRead = 0;
				long total = 0;
				while ((numBytesRead != -1) && (!Thread.interrupted())) {
					numBytesRead = ais.read(abData, 0, abData.length);
					total += numBytesRead;
					double tmpTotal = total;
					double len = (ais.getFrameLength() * 2);
					positionInTrack = tmpTotal / len * 100.0;

					if (recognizer.acceptWaveForm(abData, numBytesRead)) {
						String result = recognizer.getResult();
						VoskResultl database = gson.fromJson(result, resultType);
						processWords(database.result, durationInMillis);
					} else {
						String result = recognizer.getPartialResult();
						VoskPartial database = gson.fromJson(result, partailType);
						processWords(database.partial_result, durationInMillis);
					}
				}
                //println result;
				VoskResultl database = gson.fromJson(recognizer.getFinalResult(), resultType);
				recognizer.close();
				processWords(database.result, durationInMillis);
				positionInTrack = 100;
				if (timeCodedVisemes.size() > 0) {
					TimeCodedViseme tcLast = timeCodedVisemes.get(timeCodedVisemes.size() - 1);
					// termination sound of nothing
					TimeCodedViseme tc = new TimeCodedViseme(AudioStatus.X_NO_SOUND, tcLast.end, secLen, secLen);
					add(tc);
				}
				File json = new File(ScriptingEngine.getWorkspace().getAbsolutePath() + "/tmp-tts-visime.json");
				if (!json.exists()) {
					json.createNewFile();
				}
				String s = gson.toJson(timeCodedVisemesCache);
				BufferedWriter writer = new BufferedWriter(new FileWriter(json.getAbsolutePath()));
				writer.write(s);
				writer.close();
				timeCodedVisemesCache.clear();
			} catch (Throwable tr) {
				// BowlerStudio.printStackTrace(t);
			}
		});
		t.start();

		while (t.isAlive() && positionInTrack < 10 && (System.currentTimeMillis() - start < durationInMillis)) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				break;
			}
		}
		if (t.isAlive()) {
			t.interrupt();
		}
        //printTCV();
		println "Visemes added, start audio.. "
	}

	public AudioInputStream startProcessing(AudioInputStream ais, String TTSString) {
		timeCodedVisemes = new ArrayList<>();

		File audio = new File(ScriptingEngine.getWorkspace().getAbsolutePath() + "/tmp-tts.wav");
		try {
			long start = System.currentTimeMillis();
			System.out.println("Vosk Lip Sync Begin writing..");
			AudioSystem.write(ais, AudioFileFormat.Type.WAVE, audio);
			ais = AudioSystem.getAudioInputStream(audio);
			File text = new File(ScriptingEngine.getWorkspace().getAbsolutePath() + "/tmp-tts.txt");
			if (!text.exists())
				text.createNewFile();
			try {
				FileWriter myWriter = new FileWriter(text);
				myWriter.write(TTSString);
				myWriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			// rhubarb!
			processRaw(audio, text.getAbsolutePath());
			System.out.println("Vosk Lip Sync Done writing! took " + (System.currentTimeMillis() - start));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return ais;
	}

	public AudioStatus update(AudioStatus current, double amplitudeUnitVector, double currentRollingAverage,
			double currentDerivitiveTerm, double percent) {
		// println timeCodedVisemes
		AudioStatus ret = null;
		if (timeCodedVisemes.size() > 0) {
			TimeCodedViseme map = timeCodedVisemes.get(0);
			AudioStatus key = map.status;
			double value = map.getEndPercentage();
			if (percent > value) {
				timeCodedVisemes.remove(0);
				if (timeCodedVisemes.size() > 0)
					ret = timeCodedVisemes.get(0).status;
				else {
					 //println "\n\nERROR Audio got ahead of lip sync "+percent+"\n\n"
					ret = AudioStatus.X_NO_SOUND;
				}
			} else if (percent > map.getStartPercentage())
				ret = key;
		} else {
		  //println "\n\nERROR Audio got ahead of lip sync "+percent+"\n\n"
		}
		if (ret == null)
			ret = current;
		if (current != ret) {
			// println ret.toString()+" staarting at "+percent
		}
		return ret;

	}

}

class TabManagerDevice{
	String myName;
	boolean connected=false;
	ImageView imageView = new ImageView();
	Tab t = new Tab()
	public TabManagerDevice(String name) {
		myName=name;
		
	}
	
	String getName() {
		return myName
	}
	
	boolean connect() {
		connected=true;
		t.setContent(imageView)
		t.setText(myName)
		t.setOnCloseRequest({event ->
			disconnect()
		});
		BowlerStudioController.addObject(t, null)
		return connected
	}
	void disconnect() {
		if(connected) {
			BowlerStudioController.removeObject(t)
		}
		
	}
}

def tabHolder = DeviceManager.getSpecificDevice("TabHolder", {
	TabManagerDevice dev = new TabManagerDevice("TabHolder")
	dev.connect()
	return dev
})

HashMap<AudioStatus,Image> images = new HashMap<>()
String url = "https://github.com/madhephaestus/TextToSpeechASDRTest.git"

for(AudioStatus s:EnumSet.allOf(AudioStatus.class)) {
    //println s.parsed
    //println ScriptingEngine.getRepositoryCloneDirectory(url).getAbsolutePath()
    File f = new File(ScriptingEngine.getRepositoryCloneDirectory(url).getAbsolutePath()+ "/magenta/magenta-"+s.parsed+".png")
    //println "Loading "+f.getAbsolutePath()
	try{
	Image image = new Image(new FileInputStream(f.getAbsolutePath()));
	images.put(s, image)
	}catch(Exception ex){}
}

//AudioPlayer.setLambda (com.neuronrobotics.bowlerstudio.lipsync.VoskLipSync.get());
AudioPlayer.setLambda (new VoskLipSyncLocal());

ImageView imageView = tabHolder.imageView
laststatus=null

/*
 *  changes to the rhubarb mappings
*/

//two phonemes don't exist in my current mapping, so their original mappings will remain the same regardless
AudioStatus.ArpabetToBlair.put("jh", AudioStatus.B_KST_SOUNDS);
AudioStatus.ArpabetToBlair.put("oy", AudioStatus.F_UW_OW_W_SOUNDS);

//the silence viseme will not change
AudioStatus.ArpabetToBlair.put("-", AudioStatus.X_NO_SOUND)




// from https://github.com/CommonWealthRobotics/bowler-script-kernel/blob/development/src/main/java/com/neuronrobotics/bowlerstudio/AudioStatus.java#L92

/*
//the manual mapping of thirteen visemes, with the fn_edits
*/

AudioStatus.ArpabetToBlair.put("b", AudioStatus.A_PBM_SOUNDS)
AudioStatus.ArpabetToBlair.put("m", AudioStatus.A_PBM_SOUNDS)
AudioStatus.ArpabetToBlair.put("p", AudioStatus.A_PBM_SOUNDS)
AudioStatus.ArpabetToBlair.put("ch", AudioStatus.B_KST_SOUNDS)
AudioStatus.ArpabetToBlair.put("zh", AudioStatus.B_KST_SOUNDS)
AudioStatus.ArpabetToBlair.put("sh", AudioStatus.B_KST_SOUNDS)
AudioStatus.ArpabetToBlair.put("hh", AudioStatus.D_AA_SOUNDS)
AudioStatus.ArpabetToBlair.put("ah", AudioStatus.C_EH_AE_SOUNDS)
AudioStatus.ArpabetToBlair.put("uw", AudioStatus.D_AA_SOUNDS)
AudioStatus.ArpabetToBlair.put("uh", AudioStatus.F_UW_OW_W_SOUNDS)
AudioStatus.ArpabetToBlair.put("w", AudioStatus.D_AA_SOUNDS)
AudioStatus.ArpabetToBlair.put("ae", AudioStatus.C_EH_AE_SOUNDS)
AudioStatus.ArpabetToBlair.put("aa", AudioStatus.C_EH_AE_SOUNDS)
AudioStatus.ArpabetToBlair.put("eh", AudioStatus.C_EH_AE_SOUNDS)
AudioStatus.ArpabetToBlair.put("ih", AudioStatus.C_EH_AE_SOUNDS)
AudioStatus.ArpabetToBlair.put("d", AudioStatus.H_L_SOUNDS)
AudioStatus.ArpabetToBlair.put("g", AudioStatus.F_UW_OW_W_SOUNDS)
AudioStatus.ArpabetToBlair.put("k", AudioStatus.C_EH_AE_SOUNDS)
AudioStatus.ArpabetToBlair.put("n", AudioStatus.C_EH_AE_SOUNDS)
AudioStatus.ArpabetToBlair.put("ng", AudioStatus.F_UW_OW_W_SOUNDS)
AudioStatus.ArpabetToBlair.put("s", AudioStatus.F_UW_OW_W_SOUNDS)
AudioStatus.ArpabetToBlair.put("t", AudioStatus.F_UW_OW_W_SOUNDS)
AudioStatus.ArpabetToBlair.put("y", AudioStatus.C_EH_AE_SOUNDS)
AudioStatus.ArpabetToBlair.put("z", AudioStatus.C_EH_AE_SOUNDS)
AudioStatus.ArpabetToBlair.put("ao", AudioStatus.E_AO_ER_SOUNDS)
AudioStatus.ArpabetToBlair.put("th", AudioStatus.H_L_SOUNDS)
AudioStatus.ArpabetToBlair.put("dh", AudioStatus.H_L_SOUNDS)
AudioStatus.ArpabetToBlair.put("f", AudioStatus.I_user_defined)
AudioStatus.ArpabetToBlair.put("v", AudioStatus.I_user_defined)
AudioStatus.ArpabetToBlair.put("iy", AudioStatus.J_user_defined)
AudioStatus.ArpabetToBlair.put("l", AudioStatus.K_user_defined)
AudioStatus.ArpabetToBlair.put("r", AudioStatus.L_user_defined)
AudioStatus.ArpabetToBlair.put("aw", AudioStatus.E_AO_ER_SOUNDS)
AudioStatus.ArpabetToBlair.put("ay", AudioStatus.E_AO_ER_SOUNDS)
AudioStatus.ArpabetToBlair.put("er", AudioStatus.E_AO_ER_SOUNDS)
AudioStatus.ArpabetToBlair.put("ey", AudioStatus.C_EH_AE_SOUNDS)
AudioStatus.ArpabetToBlair.put("ow", AudioStatus.C_EH_AE_SOUNDS)
//*/



ISpeakingProgress progress ={double percent,AudioStatus status->
	if(status!=laststatus) {
		//println percent+" " +status
		laststatus=status;
		Platform.runLater({
			imageView.setImage(images.get(status))
		})
	}

}

//MaryInterface marytts = new LocalMaryInterface();

//String text = "Behold the mighty Zoltar!"
//String text = "The mighty Zoltar sees your future.  You have much to look forward to!"
//String text = "abracadabra"
//String text = "Look alive, wageslaves!"
//String text = "Once upon a midnight dreary, while I pondered, weak and weary.  Over many a quaint and curious volume of forgotten lore"
//String text = "While I nodded, nearly napping, suddenly there came a tapping, As of some one gently rapping, rapping at my chamber door.  Tis some visitor, I muttered, tapping at my chamber door.  Only this and nothing more."
//String text = "Ah distinctly I remember, it was in the bleak December.  And each separate dying ember wrought its ghost upon the floor."
//String text = "Remember remember the embers of bleak December, dismembered by November's dissenters."
//String text = "I wanna liv like common people.  I wanna do what ever common people do.  Wanna sleep with common people.  I wanna sleep with, common people.  Like you."
//String text = "To be or not to be.  That is the question.  Whether tis noble-r in the mind to suffer the slings and arrows of outrageous fortune, or to take arms against a sea of troubles and by opposing end them."
//String text = ""

AudioInputStream audio=null;//= marytts.generateAudio(text)
File file = ScriptingEngine.fileFromGit("https://github.com/madhephaestus/TextToSpeechASDRTest.git","wageslaves_wav.wav");
if (file.exists()) {

//for external storage Path
audio = AudioSystem.getAudioInputStream(file);
}
else {
throw new RuntimeException("Sound: file not found: " + fileName);
}

AudioPlayer tts = new AudioPlayer(file);
tts.setAudio(audio);
tts.setGain((float)1.0);
tts.setDaemon(true);
if(progress!=null)
	tts.setSpeakProgress(progress);
tts.start();// start the thread playing
tts.join();// wait for thread to finish before returniign


