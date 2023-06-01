@Grab(group='net.java.dev.jna', module='jna', version='5.7.0')
@Grab(group='com.alphacephei', module='vosk', version='0.3.45')

import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.neuronrobotics.bowlerstudio.AudioPlayer
import com.neuronrobotics.bowlerstudio.AudioStatus
import com.neuronrobotics.bowlerstudio.BowlerStudio
import com.neuronrobotics.bowlerstudio.IAudioProcessingLambda
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine

import java.lang.reflect.Type

import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

import org.vosk.LibVosk;
import org.vosk.Model;
import org.vosk.LogLevel;
import org.vosk.Recognizer;

class VoskResultWord{
	double conf;
	double end;
	double start;
	String word;
	String toString() {
		return "\n'"+word+"' \n\tstarts at "+start+" ends at "+end+" confidence "+conf;
	}
}

class VoskPartial{
	String partial;
	List<VoskResultWord> partial_result;
}
class VoskResultl{
	String text;
	List<VoskResultWord> result;
}
Type partailType = new TypeToken<VoskPartial>() {}.getType();
Type resultType = new TypeToken<VoskResultl>() {}.getType();

Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
Map<String, AudioStatus> ArpabetToBlair =new HashMap<>();
ArpabetToBlair.put("-", AudioStatus.X_NO_SOUND);
ArpabetToBlair.put("aa", AudioStatus.D_AA_SOUNDS);
ArpabetToBlair.put("ae", AudioStatus.D_AA_SOUNDS);
ArpabetToBlair.put("ah", AudioStatus.D_AA_SOUNDS);
ArpabetToBlair.put("ao", AudioStatus.D_AA_SOUNDS);
ArpabetToBlair.put("aw", AudioStatus.D_AA_SOUNDS);
ArpabetToBlair.put("ax", AudioStatus.D_AA_SOUNDS);
ArpabetToBlair.put("ay", AudioStatus.C_EH_AE_SOUNDS);
ArpabetToBlair.put("b", AudioStatus.A_PBM_SOUNDS);
ArpabetToBlair.put("bl", AudioStatus.A_PBM_SOUNDS);
ArpabetToBlair.put("ch", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("d", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("dx", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("dh", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("eh", AudioStatus.D_AA_SOUNDS);
ArpabetToBlair.put("em", AudioStatus.D_AA_SOUNDS);
ArpabetToBlair.put("el", AudioStatus.D_AA_SOUNDS);
ArpabetToBlair.put("en", AudioStatus.D_AA_SOUNDS);
ArpabetToBlair.put("eng", AudioStatus.D_AA_SOUNDS);
ArpabetToBlair.put("er", AudioStatus.D_AA_SOUNDS);
ArpabetToBlair.put("ey", AudioStatus.C_EH_AE_SOUNDS);
ArpabetToBlair.put("f", AudioStatus.G_F_V_SOUNDS);
ArpabetToBlair.put("g", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("hh", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("ih", AudioStatus.D_AA_SOUNDS);
ArpabetToBlair.put("iy", AudioStatus.C_EH_AE_SOUNDS);
ArpabetToBlair.put("jh", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("k", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("l", AudioStatus.H_L_SOUNDS);
ArpabetToBlair.put("m", AudioStatus.A_PBM_SOUNDS);
ArpabetToBlair.put("n", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("ng", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("nx", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("ow", AudioStatus.F_UW_OW_W_SOUNDS);
ArpabetToBlair.put("oy", AudioStatus.F_UW_OW_W_SOUNDS);
ArpabetToBlair.put("p", AudioStatus.A_PBM_SOUNDS);
ArpabetToBlair.put("q", AudioStatus.F_UW_OW_W_SOUNDS);
ArpabetToBlair.put("r", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("s", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("sh", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("t", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("th", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("uh", AudioStatus.D_AA_SOUNDS);
ArpabetToBlair.put("uw",AudioStatus.F_UW_OW_W_SOUNDS);
ArpabetToBlair.put("v", AudioStatus.G_F_V_SOUNDS);
ArpabetToBlair.put("w", AudioStatus.F_UW_OW_W_SOUNDS);
ArpabetToBlair.put("y", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("z", AudioStatus.B_KST_SOUNDS);
ArpabetToBlair.put("zh", AudioStatus.B_KST_SOUNDS);

class TimeCodedViseme{
	AudioStatus status
	double timePercent
	double start
	double end
	public TimeCodedViseme(AudioStatus st, double s, double e, double total) {
		status=st;
		start=s
		end=e
		timePercent=(end/total)*100.0
	}
	String toString() {
		return status.toString()+" "+timePercent+"% s="+start+" e="+end
	}
}

public class PhoneticDictionary {
	private Map<String, List<String>> dictionary;

	public PhoneticDictionary(File f) {
		dictionary = new HashMap<>();
		init(f)
	}

	private String normalizePhonemes(String phonemes) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < phonemes.length(); i++) {
			char c = phonemes.charAt(i);
			if (c == ' ' || (c >= 'A' && c <= 'Z')) {
				result.append(c);
			}
		}
		return result.toString().toLowerCase();
	}

	private void parseEntry(String entry, Map<String, List<String>> d) {
		String[] tokens = entry.split(" ");
		if (tokens.length < 2) {
			return null;
		}
		String word = tokens[0].trim().toLowerCase();
		if (word.endsWith(")")) {
			return null;
		}
		ArrayList<String> mine = []
		for(int i=1;i<tokens.length;i++) {
			String phonemes = normalizePhonemes(tokens[i].trim());
			mine.add(phonemes)
		}

		//println "Adding to dictionary :"+word+" = phoneme "+mine
		d.put(word, mine);
	}

	private Map<String, List<String>> parseDictionary(String dictionaryText) {
		Map<String, List<String>> dictionary = new HashMap<>();
		String[] entries = dictionaryText.split("\n");
		for (String entry : entries) {
			if (entry.startsWith(";;;")) {
				continue;
			}
			String[] result = parseEntry(entry,dictionary);
			if (result == null) {
				continue;
			}
		}
		return dictionary;
	}

	private String fetchDictionaryText(File dictionaryUrl) throws Exception {
		BufferedReader reader = new BufferedReader(new FileReader(dictionaryUrl));
		StringBuilder stringBuilder = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			stringBuilder.append(line).append("\n");
		}
		reader.close();
		return stringBuilder.toString();
	}

	public void init(File dictionaryUrl) throws Exception {
		String dictionaryText = fetchDictionaryText(dictionaryUrl);
		dictionary = parseDictionary(dictionaryText);
	}

	public List<String> find(String word) {
		return dictionary.get(word);
	}
}

AudioPlayer.setLambda(new IAudioProcessingLambda(){
			File phoneticDatabaseFile = ScriptingEngine.fileFromGit("https://github.com/madhephaestus/TextToSpeechASDRTest.git", "cmudict-0.7b.txt")
			PhoneticDictionary dict = new PhoneticDictionary(phoneticDatabaseFile)
			Model model = new Model(ScriptingEngine.getWorkspace().getAbsolutePath()+"/vosk-model-en-us-daanzu-20200905/");
			AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 60000, 16, 2, 4, 44100, false);

			Recognizer recognizer = new Recognizer(model, 120000)
			int numBytesRead=0;
			int CHUNK_SIZE = 4096;
			int bytesRead = 0;
			byte[] abData = new byte[CHUNK_SIZE];
			ArrayList<TimeCodedViseme> timeCodedVisemes = null;
			int words=0;
			private AudioStatus toStatus(String phoneme) {
				AudioStatus s = ArpabetToBlair.get(phoneme)
				if(s!=null)
					return s;
				println "Unknown phoneme "+phoneme
				return AudioStatus.X_NO_SOUND
			}
			private void addWord(VoskResultWord word,long len) {
				double secLen = ((double)len)/1000.0
				String w = word.word;
				double wordStart = word.start
				double wordEnd = word.end;
				double wordLen = wordEnd-wordStart
				List<String> phonemes =dict.find(w)
				double phonemeLength = wordLen/phonemes.size()

				//println "Word "+w+" starts at "+wordStart+" ends at "+wordEnd+" each phoneme length "+phonemeLength
				for(int i=0;i<phonemes.size();i++) {
					String phoneme = phonemes.get(i);
					AudioStatus stat = toStatus(phoneme)
					double myStart = wordStart+((i==0)?0.0:phonemeLength*((double)i-1))
					double myEnd = wordStart+phonemeLength*((double)i)
					TimeCodedViseme tc = new TimeCodedViseme(stat, myStart,myEnd, secLen)
					if(timeCodedVisemes.size()>0) {
						TimeCodedViseme tcLast = timeCodedVisemes.get(timeCodedVisemes.size()-1)
						if(tcLast.end<myStart) {
							// termination sound of nothing
							TimeCodedViseme tcSilent = new TimeCodedViseme(AudioStatus.X_NO_SOUND, tcLast.end,myStart, secLen)
							add(tcSilent)
						}
					}
					add(tc)
				}

				//println word.word+" "+phonemes

			}

			private void add(TimeCodedViseme v) {
				//println v
				timeCodedVisemes.add(v)
			}

			private void processWords(List<VoskResultWord> wordList,long len) {
				if(wordList==null)
					return

				for(;words<wordList.size();words++) {
					VoskResultWord word = wordList.get(words)
					addWord(word,len)
				}

			}
			public void processRaw(File f, String ttsLocation) {
				timeCodedVisemes = new ArrayList<>();
				words=0;
				Thread t=new Thread({
					try {
						def getAudioInputStream = AudioSystem.getAudioInputStream(f)
						long durationInMillis = 1000 * getAudioInputStream.getFrameLength() / getAudioInputStream.getFormat().getFrameRate();
						double secLen = ((double)durationInMillis)/1000.0
						AudioInputStream ais =AudioSystem.getAudioInputStream(format,getAudioInputStream);
						//println "Time of clip "+secLen+" sec"
						recognizer.reset()
						recognizer.setWords(true)
						recognizer.setPartialWords(true)
						int totalBytes = 0
						numBytesRead=0;
						while ((numBytesRead != -1) && (!Thread.interrupted())) {
							numBytesRead = ais.read(abData, 0, abData.length);
							totalBytes+=numBytesRead
							if (recognizer.acceptWaveForm(abData, numBytesRead)) {
								String result=recognizer.getResult()
								VoskResultl database = gson.fromJson(result, resultType);
								processWords(database.result,durationInMillis)
							} else {
								String result = recognizer.getPartialResult()
								VoskPartial database = gson.fromJson(result, partailType);
								processWords(database.partial_result,durationInMillis)
							}
						}
						VoskResultl database = gson.fromJson(recognizer.getFinalResult(), resultType);
						processWords(database.result,durationInMillis)
						if(timeCodedVisemes.size()>0) {
							TimeCodedViseme tcLast = timeCodedVisemes.get(timeCodedVisemes.size()-1)
							// termination sound of nothing
							TimeCodedViseme tc = new TimeCodedViseme(AudioStatus.X_NO_SOUND, tcLast.end,secLen, secLen)
							timeCodedVisemes.add(tc)
						}
					}catch(Throwable t) {
						BowlerStudio.printStackTrace(t)
					}
				})
				t.start()
				while(t.isAlive() && timeCodedVisemes.size()==0) {
					Thread.sleep(1)
				}
				println "Visemes added, start audio.. "
			}
			public AudioInputStream startProcessing(AudioInputStream ais, String TTSString) {
				//recognizer = new Recognizer(model, 120000)
				File audio = new File(ScriptingEngine.getWorkspace().getAbsolutePath() + "/tmp-tts.wav");
				try {
					long start = System.currentTimeMillis()
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
					System.out.println("Vosk Lip Sync Done writing! took "+(System.currentTimeMillis()-start));
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				return ais;
			}
			public AudioStatus update(AudioStatus current, double amplitudeUnitVector, double currentRollingAverage, double currentDerivitiveTerm, double percent) {
				if (timeCodedVisemes.size() > 0) {
					TimeCodedViseme map = timeCodedVisemes.get(0);
					AudioStatus key = map.status
					double value = map.timePercent
					if (percent > value) {
						timeCodedVisemes.remove(0);
						return timeCodedVisemes.get(0).status
					}
					return key;
				}
				return current;
			}
		});