
import java.lang.reflect.Type

import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine
import javax.sound.sampled.UnsupportedAudioFileException

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
import com.neuronrobotics.sdk.util.ThreadUtil

import javafx.scene.control.Tab
import javafx.scene.image.Image
import javafx.application.Platform
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
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
		String w = word.word;
		if (w == null)
			return;

		double wordStart = word.start;
		double wordEnd = word.end;
		double wordLen = wordEnd - wordStart;
		ArrayList<String> phonemes = dict.find(w);
		if (phonemes == null) {
			// println "\n\n unknown word "+w+"\n\n"
			return;
		}

		double phonemeLength = wordLen / phonemes.size();
		//@finn this is where to adjust the lead/lag of the lip sync with the audio playback
		double timeLeadLag = 0.2
		for (int i = 0; i < phonemes.size(); i++) {
			String phoneme = phonemes.get(i);
			AudioStatus stat = toStatus(phoneme);
			double myStart = wordStart + phonemeLength * ((double) i)+timeLeadLag;
			double myEnd = wordStart + phonemeLength * ((double) (i + 1))+timeLeadLag;
			TimeCodedViseme tc = new TimeCodedViseme(stat, myStart, myEnd, secLen);
			if (timeCodedVisemes.size() > 0) {
				TimeCodedViseme tcLast = timeCodedVisemes.get(timeCodedVisemes.size() - 1);
				if (tcLast.end < myStart) {
					// termination sound of nothing
					TimeCodedViseme tcSilent = new TimeCodedViseme(AudioStatus.X_NO_SOUND, tcLast.end, myStart, secLen);
					add(tcSilent);
				}
			}
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

		while (t.isAlive() && positionInTrack < 1 && (System.currentTimeMillis() - start < durationInMillis)) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				break;
			}
		}
		if (t.isAlive()) {
			t.interrupt();
		}
		// println "Visemes added, start audio.. "
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
					// println "\n\nERROR Audio got ahead of lip sync "+percent+"\n\n"
					ret = AudioStatus.X_NO_SOUND;
				}
			} else if (percent > map.getStartPercentage())
				ret = key;
		} else {
			// println "\n\nERROR Audio got ahead of lip sync "+percent+"\n\n"
		}
		if (ret == null)
			ret = current;
		if (current != ret) {
			// println ret.toString()+" staarting at "+percent
		}
		return ret;

	}

}


HashMap<AudioStatus,Image> images = new HashMap<>()
String url = "https://github.com/madhephaestus/TextToSpeechASDRTest.git"
for(AudioStatus s:EnumSet.allOf(AudioStatus.class)) {
	File f = new File(ScriptingEngine.getRepositoryCloneDirectory(url).getAbsolutePath()+ "/img/lisa-"+s.parsed+".png")
	println "Loading "+f.getAbsolutePath()
	Image image = new Image(new FileInputStream(f.getAbsolutePath()));
	images.put(s, image)
}

//AudioPlayer.setLambda (com.neuronrobotics.bowlerstudio.lipsync.VoskLipSync.get());
AudioPlayer.setLambda (new VoskLipSyncLocal());

ImageView imageView = new ImageView(images.get(AudioStatus.X_NO_SOUND));
laststatus=null

// from https://github.com/CommonWealthRobotics/bowler-script-kernel/blob/development/src/main/java/com/neuronrobotics/bowlerstudio/AudioStatus.java#L92
AudioStatus.ArpabetToBlair.put("-", AudioStatus.X_NO_SOUND)

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


