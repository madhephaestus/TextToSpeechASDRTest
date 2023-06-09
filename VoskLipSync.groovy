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
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException

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
Map<String, AudioStatus>ArpabetToBlair =new HashMap<>();
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
	double start
	double end
	double total
	public TimeCodedViseme(AudioStatus st, double s, double e, double t) {
		status=st;
		start=s
		end=e
		total=t
	}
	double getStartPercentage() {
		return ((start/total)*100.0)
	}
	double getEndPercentage() {
		return ((end/total)*100.0)
	}
	String toString() {
		return status.toString()+" start percent "+getStartPercentage()+" ends at "+getEndPercentage() 
	}
}

public class PhoneticDictionary {
	private Map<String, List<String>> dictionary;
	Map<String, AudioStatus>ArpabetToBlair;
	public PhoneticDictionary(File f,Map<String, AudioStatus>a) {
		dictionary = new HashMap<>();
		ArpabetToBlair=a
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

	private void parseEntry(String entry, Map<String, ArrayList<String>> d) {
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

	private Map<String, ArrayList<String>> parseDictionary(String dictionaryText) {
		Map<String, ArrayList<String>> dictionary = new HashMap<>();
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

	public ArrayList<String> find(String w) {
		ArrayList<String>  extra=null
		if(w.endsWith("n't")) {
			String newW =w.substring(0,w.length()-3)
			println "Contraction reduced "+newW+" from "+w
			w=newW
			extra =["n", "t"]
		}
		if(w.endsWith("'ar")) {
			String newW =w.substring(0,w.length()-3)
			println "Contraction reduced "+newW+" from "+w
			w=newW
			extra =[ "r"]
		}
		if(w.endsWith("'ll")) {
			String newW =w.substring(0,w.length()-3)
			println "Contraction reduced "+newW+" from "+w
			w=newW
			extra =["uw", "l"]
		}
		if(w.endsWith("'ve")) {
			String newW =w.substring(0,w.length()-3)
			println "Contraction reduced "+newW+" from "+w
			w=newW
			extra =["v"]
		}
		if(w.endsWith("'re")) {
			String newW =w.substring(0,w.length()-3)
			println "Contraction reduced "+newW+" from "+w
			w=newW
			extra =["r"]
		}
		if(w.endsWith("'s")) {
			String newW =w.substring(0,w.length()-2)
			println "Contraction reduced "+newW+" from "+w
			w=newW
			extra =["s"]
		}
		if(w.endsWith("'d")) {
			String newW =w.substring(0,w.length()-2)
			println "Contraction reduced "+newW+" from "+w
			w=newW
			extra =["d"]
		}
		ArrayList<String>  phonemes = []
		ArrayList<String> dictionaryGet = dictionary.get(w)
		if(dictionaryGet==null) {
			dictionaryGet = []
			byte[] bytes = w.getBytes()
			println "Sounding out "+w
			for(int i=0;i<w.length();i++) {
				String charAt = new String(bytes[i]);
				println charAt
				if(ArpabetToBlair.get(charAt)!=null) {
					dictionaryGet.add(charAt)
				}else {
					for(String s:ArpabetToBlair.keySet()) {
						if(s.contains(charAt)) {
							dictionaryGet.add(s)
							break;
						}
					}
				}
			}
			println "New Word: "+w+" "+dictionaryGet
			dictionary.put(w,dictionaryGet)
		}
		phonemes.addAll(dictionaryGet)
		if(extra!=null) {
			phonemes.addAll(extra)
		}
		return phonemes;
	}
}
String modelName = "vosk-model-en-us-daanzu-20200905";
String pathTOModel = ScriptingEngine.getWorkspace().getAbsolutePath()+"/"+modelName+".zip"
File zipfile = new File(pathTOModel)

if(!zipfile.exists()) {

	String urlStr = "https://alphacephei.com/vosk/models/"+modelName+".zip"
	URL url = new URL(urlStr);
	BufferedInputStream bis = new BufferedInputStream(url.openStream());
	FileOutputStream fis = new FileOutputStream(zipfile);
	byte[] buffer = new byte[1024];
	int count = 0;
	System.out.println("Downloading Vosk Model "+modelName)
	while ((count = bis.read(buffer, 0, 1024)) != -1) {
		fis.write(buffer, 0, count);
		System.out.print(".")
	}
	fis.close();
	bis.close();

	String source = zipfile.getAbsolutePath();
	String destination = ScriptingEngine.getWorkspace().getAbsolutePath() ;
	System.out.println("Unzipping Vosk Model "+modelName)
	ZipFile zipFile = new ZipFile(source);
	zipFile.extractAll(destination);

}
Model model = new Model(ScriptingEngine.getWorkspace().getAbsolutePath()+"/"+modelName+"/");

AudioPlayer.setLambda(new IAudioProcessingLambda(){
	
			File phoneticDatabaseFile = ScriptingEngine.fileFromGit("https://github.com/madhephaestus/TextToSpeechASDRTest.git", "cmudict-0.7b.txt")
			PhoneticDictionary dict = new PhoneticDictionary(phoneticDatabaseFile,ArpabetToBlair)
			AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 60000, 16, 2, 4, 44100, false);
//
//			Recognizer recognizer = new Recognizer(model, 120000)
			int numBytesRead=0;
			int CHUNK_SIZE = 4096;
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
				if(w==null)
					return;

				double wordStart = word.start
				double wordEnd = word.end;
				double wordLen = wordEnd-wordStart
				ArrayList<String> phonemes =dict.find(w)
				if(phonemes==null) {
					println "\n\n unknown word "+w+"\n\n"
					return;
				}

				double phonemeLength = wordLen/phonemes.size()
				for(int i=0;i<phonemes.size();i++) {
					String phoneme = phonemes.get(i);
					AudioStatus stat = toStatus(phoneme)
					double myStart = wordStart+phonemeLength*((double)i)
					double myEnd = wordStart+phonemeLength*((double)(i+1))
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

				//println "Word "+w+" starts at "+wordStart+" ends at "+wordEnd+" each phoneme length "+phonemeLength+" "+phonemes+" "+timeCodedVisemes

			}

			private void add(TimeCodedViseme v) {
				//println "Adding "+ v
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
				
				words=0;
				double positionInTrack =0;
				def getAudioInputStream = AudioSystem.getAudioInputStream(f)
				long durationInMillis = 1000 * getAudioInputStream.getFrameLength() / getAudioInputStream.getFormat().getFrameRate();
				long start= System.currentTimeMillis()
				Thread t=new Thread({
					try {
						
						double secLen = ((double)durationInMillis)/1000.0
						AudioInputStream ais =AudioSystem.getAudioInputStream(format,getAudioInputStream);
						Recognizer recognizer = new Recognizer(model, 120000)
						recognizer.setWords(true)
						recognizer.setPartialWords(true)
						numBytesRead=0;
						long total=0
						while ((numBytesRead != -1) && (!Thread.interrupted())) {
							numBytesRead = ais.read(abData, 0, abData.length);
							total+=numBytesRead
							double tmpTotal = total;
							double len = (ais.getFrameLength() * 2);
							positionInTrack = tmpTotal / len * 100.0;
							
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
						recognizer.close()
						processWords(database.result,durationInMillis)
						positionInTrack=100;
						if(timeCodedVisemes.size()>0) {
							TimeCodedViseme tcLast = timeCodedVisemes.get(timeCodedVisemes.size()-1)
							// termination sound of nothing
							TimeCodedViseme tc = new TimeCodedViseme(AudioStatus.X_NO_SOUND, tcLast.end,secLen, secLen)
							add(tc)
						}
					}catch(Throwable t) {
						BowlerStudio.printStackTrace(t)
					}
				})
				t.start()
				
				while(t.isAlive() && positionInTrack<1 && (System.currentTimeMillis()-start<durationInMillis)) {
					Thread.sleep(1)
				}
				if(t.isAlive())
					t.interrupt()
				println "Visemes added, start audio.. "
			}
			public AudioInputStream startProcessing(AudioInputStream ais, String TTSString) {
				timeCodedVisemes = new ArrayList<>();
				
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
				//println timeCodedVisemes
				AudioStatus ret=null
				if (timeCodedVisemes.size() > 0) {
					TimeCodedViseme map = timeCodedVisemes.get(0);
					AudioStatus key = map.status
					double value = map.getEndPercentage()
					if (percent > value) {
						timeCodedVisemes.remove(0);
						if(timeCodedVisemes.size()>0)
							ret = timeCodedVisemes.get(0).status
						else {
							println "\n\nERROR Audio got ahead of lip sync "+percent+"\n\n"
							ret= AudioStatus.X_NO_SOUND
						}
					}else if(percent>map.getStartPercentage())
						ret=key;
				}else {
					println "\n\nERROR Audio got ahead of lip sync "+percent+"\n\n"
				}
				if(ret==null)
					ret=current;
				if(current!=ret) {
					//println ret.toString()+" staarting at "+percent
				}	
				return ret;
			}
		});