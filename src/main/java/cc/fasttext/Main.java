package cc.fasttext;

import cc.fasttext.io.FormatUtils;
import cc.fasttext.io.IOStreams;
import cc.fasttext.io.PrintLogs;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The main class to run FastText as application from command line.
 * All public methods: the output goes to std:out and std:err, the input comes from std:in or command line (through file-references maybe).
 * This and only this class is allowed to work directly with standard i/o and perform exit.
 * Unlike original cpp-class it contains also parsing args which has been moved from {@link Args args.cc, args.h}.
 * <p>
 * <a href='https://github.com/facebookresearch/fastText/blob/master/src/main.cc'>main.cc</a>
 * <a href='https://github.com/facebookresearch/fastText/blob/master/src/main.h'>main.h</a>
 */
public class Main {

    private static FastText.Factory factory = FastText.DEFAULT_FACTORY;

    public static void setFileSystem(IOStreams fileSystem) {
        factory = factory.setFileSystem(fileSystem);
    }

    public static IOStreams fileSystem() {
        return factory.getFileSystem();
    }

    /**
     * <pre>{@code void test(const std::vector<std::string>& args) {
     *  if (args.size() < 4 || args.size() > 5) {
     *      printTestUsage();
     *      exit(EXIT_FAILURE);
     *  }
     *  int32_t k = 1;
     *  if (args.size() >= 5) {
     *      k = std::stoi(args[4]);
     *  }
     *  FastText fasttext;
     *  fasttext.loadModel(args[2]);
     *  std::string infile = args[3];
     *  if (infile == "-") {
     *      fasttext.test(std::cin, k);
     *  } else {
     *      std::ifstream ifs(infile);
     *  if (!ifs.is_open()) {
     *      std::cerr << "Test file cannot be opened!" << std::endl;
     *      exit(EXIT_FAILURE);
     *  }
     *  fasttext.test(ifs, k);
     *  ifs.close();
     *  }
     *  exit(0);
     * }}</pre>
     *
     * @param input array of args (example: "predict-prob out\dbpedia.bin out\dbpedia.test 7")
     * @throws IOException              in case something is wrong while operating with in/out
     * @throws IllegalArgumentException in case wrong input
     */
    public static void test(String[] input) throws IOException, IllegalArgumentException {
        int k = 1;
        if (input.length == 4) {
            k = Integer.parseInt(input[3]);
        } else if (input.length != 3) {
            throw Usage.TEST.toException();
        }
        FastText fasttext = loadModel(input[1]);
        String infile = input[2];
        FastText.TestInfo res = "-".equals(infile) ? fasttext.test(System.in, k) : fasttext.test(infile, k);
        System.out.println(res.toString());
    }

    /**
     * <pre>{@code void predict(const std::vector<std::string>& args) {
     *  if (args.size() < 4 || args.size() > 5) {
     *      printPredictUsage();
     *      exit(EXIT_FAILURE);
     *  }
     *  int32_t k = 1;
     *  if (args.size() >= 5) {
     *      k = std::stoi(args[4]);
     *  }
     *  bool print_prob = args[1] == "predict-prob";
     *  FastText fasttext;
     *  fasttext.loadModel(std::string(args[2]));
     *  std::string infile(args[3]);
     *  if (infile == "-") {
     *      fasttext.predict(std::cin, k, print_prob);
     *  } else {
     *      std::ifstream ifs(infile);
     *      if (!ifs.is_open()) {
     *          std::cerr << "Input file cannot be opened!" << std::endl;
     *          exit(EXIT_FAILURE);
     *      }
     *      fasttext.predict(ifs, k, print_prob);
     *      ifs.close();
     *  }
     *  exit(0);
     * }}</pre>
     *
     * @param input array of args (example: "predict-prob out\dbpedia.bin - 7")
     * @throws IOException              in case something is wrong with in/out
     * @throws IllegalArgumentException wrong inputs
     */
    public static void predict(String[] input) throws IOException, IllegalArgumentException {
        int k = 1;
        if (input.length == 4) {
            k = Integer.parseInt(input[3]);
        } else if (input.length != 3) {
            throw Usage.PREDICT.toException();
        }
        boolean printProb = "predict-prob".equalsIgnoreCase(input[0]);
        FastText fasttext = loadModel(input[1]);
        String file = input[2];
        try (Stream<Map<String, Float>> res = "-".equals(file) ? fasttext.predict(System.in, k) : fasttext.predict(file, k)) {
            res.map(map -> map.entrySet().stream()
                    .map(e -> {
                        String line = e.getKey();
                        if (printProb) {
                            line += " " + FormatUtils.toString(e.getValue(), 6);
                        }
                        return line;
                    }).collect(Collectors.joining(" ")))
                    .forEach(System.out::println);
        }
    }

    /**
     * <pre>{@code void printWordVectors(const std::vector<std::string> args) {
     *  if (args.size() != 3) {
     *      printPrintWordVectorsUsage();
     *      exit(EXIT_FAILURE);
     *  }
     *  FastText fasttext;
     *  fasttext.loadModel(std::string(args[2]));
     *  std::string word;
     *  Vector vec(fasttext.getDimension());
     *  while (std::cin >> word) {
     *      fasttext.getWordVector(vec, word);
     *      std::cout << word << " " << vec << std::endl;
     *  }
     *  exit(0);
     * }}</pre>
     *
     * @param input input parameters, array of strings, not null
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if input is wrong
     */
    public static void printWordVectors(String[] input) throws IOException, IllegalArgumentException {
        if (input.length != 2) {
            throw Usage.PRINT_WORD_VECTORS.toException();
        }
        FastText fasttext = loadModel(input[1]);
        Scanner sc = new Scanner(System.in);
        while (sc.hasNextLine()) {
            String word = sc.nextLine();
            Vector vec = fasttext.getWordVector(word);
            System.out.println(word + " " + vec);
        }
    }

    /**
     * <pre>{@code void printSentenceVectors(const std::vector<std::string> args) {
     *  if (args.size() != 3) {
     *      printPrintSentenceVectorsUsage();
     *      exit(EXIT_FAILURE);
     *  }
     *  FastText fasttext;
     *  fasttext.loadModel(std::string(args[2]));
     *  Vector svec(fasttext.getDimension());
     *  while (std::cin.peek() != EOF) {
     *      fasttext.getSentenceVector(std::cin, svec);
     *      // Don't print sentence
     *      std::cout << svec << std::endl;
     *  }
     *  exit(0);
     * }}</pre>
     *
     * @param input input parameters, array of strings, not null
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if input is wrong
     */
    public static void printSentenceVectors(String[] input) throws IOException, IllegalArgumentException {
        if (input.length != 2) {
            throw Usage.PRINT_SENTENCE_VECTORS.toException();
        }
        FastText fasttext = loadModel(input[1]);
        Scanner sc = new Scanner(System.in);
        while (sc.hasNextLine()) {
            Vector res = fasttext.getSentenceVector(sc.nextLine());
            System.out.println(res);
        }
    }

    /**
     * <pre>{@code void printNgrams(const std::vector<std::string> args) {
     *  if (args.size() != 4) {
     *      printPrintNgramsUsage();
     *      exit(EXIT_FAILURE);
     *  }
     *  FastText fasttext;
     *  fasttext.loadModel(std::string(args[2]));
     *  fasttext.ngramVectors(std::string(args[3]));
     *  exit(0);
     * }}</pre>
     *
     * @param input input parameters, array of strings, not null
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if input is wrong
     */
    public static void printNgrams(String[] input) throws IOException, IllegalArgumentException {
        if (input.length != 3) {
            throw Usage.PRINT_NGRAMS.toException();
        }
        FastText fasttext = loadModel(input[1]);
        fasttext.ngramVectors(input[2]).forEach((subword, vec) -> System.out.println(subword + " " + vec));
    }

    /**
     * <pre>{@code void nn(const std::vector<std::string> args) {
     *  int32_t k;
     *  if (args.size() == 3) {
     *      k = 10;
     *  } else if (args.size() == 4) {
     *      k = std::stoi(args[3]);
     *  } else {
     *      printNNUsage();
     *      exit(EXIT_FAILURE);
     *  }
     *  FastText fasttext;
     *  fasttext.loadModel(std::string(args[2]));
     *  fasttext.nn(k);
     *  exit(0);
     * }}</pre>
     *
     * @param input input parameters, array of strings, not null
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if input is wrong
     */
    public static void nn(String[] input) throws IOException, IllegalArgumentException {
        int k = 10;
        if (input.length == 3) {
            k = Integer.parseInt(input[2]);
        } else if (input.length != 2) {
            throw Usage.NN.toException();
        }
        FastText fasttext = loadModel(input[1]);
        fasttext.getPrecomputedWordVectors();
        Scanner sc = new Scanner(System.in);
        PrintStream out = System.out;
        while (true) {
            out.println("Query word?");
            String line;
            try {
                line = sc.next();
            } catch (NoSuchElementException e) {
                // ctrl+d
                return;
            }
            fasttext.nn(k, line).forEach((s, f) -> out.println(s + " " + FormatUtils.toString(f)));
        }
    }

    /**
     * <pre>{@code void analogies(const std::vector<std::string> args) {
     *  int32_t k;
     *  if (args.size() == 3) {
     *      k = 10;
     *  } else if (args.size() == 4) {
     *      k = std::stoi(args[3]);
     *  } else {
     *      printAnalogiesUsage();
     *      exit(EXIT_FAILURE);
     *  }
     *  FastText fasttext;
     *  fasttext.loadModel(std::string(args[2]));
     *  fasttext.analogies(k);
     *  exit(0);
     * }}</pre>
     *
     * @param input input parameters, array of strings, not null
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if input is wrong
     */
    public static void analogies(String[] input) throws IOException, IllegalArgumentException {
        int k = 10;
        if (input.length == 3) {
            k = Integer.parseInt(input[2]);
        } else if (input.length != 2) {
            throw Usage.ANALOGIES.toException();
        }
        FastText fasttext = loadModel(input[1]);
        fasttext.getPrecomputedWordVectors();
        Scanner sc = new Scanner(System.in);
        PrintStream out = System.out;
        while (true) {
            out.println("Query triplet (A - B + C)?");
            List<String> words = new ArrayList<>();
            while (words.size() < 3) {
                String word;
                try {
                    word = sc.next();
                } catch (NoSuchElementException e) {
                    // ctrl+d
                    return;
                }
                words.add(word);
            }
            fasttext.analogies(k, words.get(0), words.get(1), words.get(2))
                    .forEach((s, f) -> out.println(s + " " + FormatUtils.toString(f)));
        }
    }


    /**
     * <pre>{@code void train(const std::vector<std::string> args) {
     *  std::shared_ptr<Args> a = std::make_shared<Args>();
     *  a->parseArgs(args);
     *  FastText fasttext;
     *  fasttext.train(a);
     *  fasttext.saveModel();
     *  fasttext.saveVectors();
     *  if (a->saveOutput > 0) {
     *      fasttext.saveOutput();
     *  }
     * }}</pre>
     *
     * @param input input parameters, array of strings, not null
     * @throws IOException              if an I/O error occurs
     * @throws ExecutionException       if any error occurs while training in several threads.
     * @throws IllegalArgumentException if input is wrong
     */
    public static void train(String[] input) throws IOException, ExecutionException, IllegalArgumentException {
        if (input.length == 0) {
            throw Usage.TRAIN.toException("Empty args specified.", Usage.ARGS);
        }
        Map<String, String> args = toMap(input);
        Args.ModelName type = Args.ModelName.fromName(input[0]);

        String data = args.get("-input");
        if (StringUtils.isEmpty(data)) {
            throw Usage.TRAIN.toException("Empty -input", Usage.ARGS);
        } else if (!fileSystem().canRead(data)) {
            throw Usage.TRAIN.toException("Wrong -input: can't read " + data, Usage.ARGS);
        }
        String model = args.get("-output");
        if (StringUtils.isEmpty(model)) {
            throw Usage.TRAIN.toException("Empty -output", Usage.ARGS);
        }
        String out = null;
        if (args.containsKey("-saveOutput")) {
            out = model + ".output";
        }
        String bin = model + ".bin";
        String vec = model + ".vec";
        if (Stream.of(bin, vec, out).filter(Objects::nonNull).anyMatch(file -> !fileSystem().canWrite(file))) {
            throw Usage.TRAIN.toException("Wrong -output: can't write model " + data, Usage.ARGS);
        }
        String vectors = args.get("-pretrainedVectors");
        if (!StringUtils.isEmpty(vectors) && !fileSystem().canRead(vectors)) {
            throw Usage.TRAIN.toException("Wrong -pretrainedVectors: can't read " + vectors, Usage.ARGS);
        }
        PrintLogs.Level verbose = parseVerbose(args, Usage.TRAIN);
        FastText fasttext = factory.setLogs(createStdErrLogger(verbose)).train(parseArgs(type, args), data, vectors);
        fasttext.saveModel(bin);
        fasttext.saveVectors(vec);
        if (out == null) return;
        fasttext.saveOutput(out);
    }

    /**
     * <pre>{@code void quantize(const std::vector<std::string>& args) {
     *  std::shared_ptr<Args> a = std::make_shared<Args>();
     *  if (args.size() < 3) {
     *      printQuantizeUsage();
     *      a->printHelp();
     *      exit(EXIT_FAILURE);
     *  }
     *  a->parseArgs(args);
     *  FastText fasttext;
     *  // parseArgs checks if a->output is given.
     *  fasttext.loadModel(a->output + ".bin");
     *  fasttext.quantize(a);
     *  fasttext.saveModel();
     *  exit(0);
     * }}</pre>
     *
     * @param input input parameters, array of strings, not null
     * @throws IOException              if an I/O error occurs during load or retraining.
     * @throws ExecutionException       if any error occurs while training in several threads.
     * @throws IllegalArgumentException if input is wrong
     */
    public static void quantize(String[] input) throws IOException, ExecutionException, IllegalArgumentException {
        if (input.length == 0) {
            throw Usage.QUANTIZE.toException("Empty args specified.", Usage.ARGS);
        }
        Map<String, String> argsMap = toMap(input);
        String model = argsMap.get("-output");
        if (StringUtils.isEmpty(model)) {
            throw Usage.QUANTIZE.toException("No model (-output)", Usage.ARGS);
        }
        String bin = model + ".bin";
        if (!fileSystem().canRead(bin)) {
            throw Usage.QUANTIZE.toException("Wrong -output: can't read file " + bin, Usage.ARGS);
        }
        String data = null;
        if (argsMap.containsKey("-retrain")) {
            data = argsMap.get("-input");
            if (StringUtils.isEmpty(data)) {
                throw Usage.QUANTIZE.toException("Wrong args: -input is required if -retrain specified.", Usage.ARGS);
            } else if (!fileSystem().canRead(data)) {
                throw Usage.QUANTIZE.toException("Wrong -input: can't read file " + data, Usage.ARGS);
            }
        }
        String ftz = model + ".ftz";
        String vec = model + ".vec";
        if (!fileSystem().canWrite(ftz) || !fileSystem().canWrite(vec)) {
            throw Usage.QUANTIZE.toException("Wrong -output: can't write model " + model, Usage.ARGS);
        }
        if (argsMap.containsKey("-saveOutput")) {
            throw Usage.QUANTIZE.toException("Option -saveOutput is not supported for quantized models", Usage.ARGS);
        }
        PrintLogs.Level verbose = parseVerbose(argsMap, Usage.QUANTIZE);
        Args args = parseArgs(Args.ModelName.SUP, argsMap);
        FastText fasttext = factory.setLogs(createStdErrLogger(verbose)).load(bin).quantize(args, data);
        fasttext.saveModel(ftz);
        fasttext.saveVectors(vec);
    }

    public static void main(String... args) {
        try {
            run(args);
        } catch (Usage.WrongInputException e) {
            System.out.print(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void run(String... args) throws Exception {
        if (args.length < 1) {
            throw Usage.COMMON.toException();
        }
        String command = args[0];
        if ("skipgram".equalsIgnoreCase(command) || "cbow".equalsIgnoreCase(command) || "supervised".equalsIgnoreCase(command)) {
            train(args);
        } else if ("quantize".equalsIgnoreCase(command)) {
            quantize(args);
        } else if ("test".equalsIgnoreCase(command)) {
            test(args);
        } else if ("print-word-vectors".equalsIgnoreCase(command)) {
            printWordVectors(args);
        } else if ("print-sentence-vectors".equalsIgnoreCase(command)) {
            printSentenceVectors(args);
        } else if ("print-ngrams".equalsIgnoreCase(command)) {
            printNgrams(args);
        } else if ("nn".equalsIgnoreCase(command)) {
            nn(args);
        } else if ("analogies".equalsIgnoreCase(command)) {
            analogies(args);
        } else if ("predict".equalsIgnoreCase(command) || "predict-prob".equalsIgnoreCase(command)) {
            predict(args);
        } else {
            throw Usage.COMMON.toException();
        }
    }

    /**
     * A factory method to load new {@link FastText model}.
     *
     * @param file, String, not null, the reference to file
     * @return {@link FastText}
     * @throws IOException if something is wrong.
     */
    private static FastText loadModel(String file) throws IOException {
        return factory.setLogs(createStdErrLogger(PrintLogs.Level.INFO)).load(file);
    }

    /**
     * Creates a ft-logger based on <code>System.err</code>
     *
     * @param level {@link cc.fasttext.io.PrintLogs.Level}
     * @return {@link PrintLogs}
     */
    public static PrintLogs createStdErrLogger(PrintLogs.Level level) {
        return level.createLogger(System.err);
    }

    private static PrintLogs.Level parseVerbose(Map<String, String> args, Usage usage) {
        if (!args.containsKey("-verbose")) return PrintLogs.Level.ALL;
        try {
            return PrintLogs.Level.at(Integer.parseInt(args.get("-verbose")));
        } catch (NumberFormatException e) {
            throw usage.toException(e.getMessage());
        }
    }

    /**
     * from args.cc:
     * <pre>{@code void Args::parseArgs(const std::vector<std::string>& args) {
     *  std::string command(args[1]);
     *  if (command == "supervised") {
     *      model = model_name::sup;
     *      loss = loss_name::softmax;
     *      minCount = 1;
     *      minn = 0;
     *      maxn = 0;
     *      lr = 0.1;
     *  } else if (command == "cbow") {
     *      model = model_name::cbow;
     *  }
     *  int ai = 2;
     *  while (ai < args.size()) {
     *      if (args[ai][0] != '-') {
     *          std::cerr << "Provided argument without a dash! Usage:" << std::endl;
     *          printHelp();
     *          exit(EXIT_FAILURE);
     *      }
     *      if (args[ai] == "-h") {
     *          std::cerr << "Here is the help! Usage:" << std::endl;
     *          printHelp();
     *          exit(EXIT_FAILURE);
     *      } else if (args[ai] == "-input") {
     *          input = std::string(args[ai + 1]);
     *      } else if (args[ai] == "-test") {
     *          test = std::string(args[ai + 1]);
     *      } else if (args[ai] == "-output") {
     *          output = std::string(args[ai + 1]);
     *      } else if (args[ai] == "-lr") {
     *          lr = std::stof(args[ai + 1]);
     *      } else if (args[ai] == "-lrUpdateRate") {
     *          lrUpdateRate = std::stoi(args[ai + 1]);
     *      } else if (args[ai] == "-dim") {
     *          dim = std::stoi(args[ai + 1]);
     *      } else if (args[ai] == "-ws") {
     *          ws = std::stoi(args[ai + 1]);
     *      } else if (args[ai] == "-epoch") {
     *          epoch = std::stoi(args[ai + 1]);
     *      } else if (args[ai] == "-minCount") {
     *          minCount = std::stoi(args[ai + 1]);
     *      } else if (args[ai] == "-minCountLabel") {
     *          minCountLabel = std::stoi(args[ai + 1]);
     *      } else if (args[ai] == "-neg") {
     *          neg = std::stoi(args[ai + 1]);
     *      } else if (args[ai] == "-wordNgrams") {
     *          wordNgrams = std::stoi(args[ai + 1]);
     *      } else if (args[ai] == "-loss") {
     *          if (args[ai + 1] == "hs") {
     *              loss = loss_name::hs;
     *          } else if (args[ai + 1] == "ns") {
     *              loss = loss_name::ns;
     *          } else if (args[ai + 1] == "softmax") {
     *              loss = loss_name::softmax;
     *          } else {
     *              std::cerr << "Unknown loss: " << args[ai + 1] << std::endl;
     *              printHelp();
     *              exit(EXIT_FAILURE);
     *          }
     *      } else if (args[ai] == "-bucket") {
     *          bucket = std::stoi(args[ai + 1]);
     *      } else if (args[ai] == "-minn") {
     *          minn = std::stoi(args[ai + 1]);
     *      } else if (args[ai] == "-maxn") {
     *          maxn = std::stoi(args[ai + 1]);
     *      } else if (args[ai] == "-thread") {
     *          thread = std::stoi(args[ai + 1]);
     *      } else if (args[ai] == "-t") {
     *          t = std::stof(args[ai + 1]);
     *      } else if (args[ai] == "-label") {
     *          label = std::string(args[ai + 1]);
     *      } else if (args[ai] == "-verbose") {
     *          verbose = std::stoi(args[ai + 1]);
     *      } else if (args[ai] == "-pretrainedVectors") {
     *          pretrainedVectors = std::string(args[ai + 1]);
     *      } else if (args[ai] == "-saveOutput") {
     *          saveOutput = std::stoi(args[ai + 1]);
     *      } else if (args[ai] == "-qnorm") {
     *          qnorm = true; ai--;
     *      } else if (args[ai] == "-retrain") {
     *          retrain = true; ai--;
     *      } else if (args[ai] == "-qout") {
     *          qout = true; ai--;
     *      } else if (args[ai] == "-cutoff") {
     *          cutoff = std::stoi(args[ai + 1]);
     *      } else if (args[ai] == "-dsub") {
     *          dsub = std::stoi(args[ai + 1]);
     *      } else {
     *          std::cerr << "Unknown argument: " << args[ai] << std::endl;
     *          printHelp();
     *          exit(EXIT_FAILURE);
     *      }
     *      ai += 2;
     *  }
     *  if (input.empty() || output.empty()) {
     *      std::cerr << "Empty input or output path." << std::endl;
     *      printHelp();
     *      exit(EXIT_FAILURE);
     *  }
     *  if (wordNgrams <= 1 && maxn == 0) {
     *      bucket = 0;
     *  }
     * }}</pre>
     *
     * @param model {@link Args.ModelName}, not null
     * @param args  Map of input parameters, see {@link #toMap(String...)}
     * @return {@link Args}
     * @throws IllegalArgumentException if input is wrong
     */
    public static Args parseArgs(Args.ModelName model, Map<String, String> args) throws IllegalArgumentException {
        Args.Builder builder = new Args.Builder().setModel(model);
        putIntegerArg(args, "-lrUpdateRate", builder::setLRUpdateRate);
        putIntegerArg(args, "-dim", builder::setDim);
        putIntegerArg(args, "-ws", builder::setWS);
        putIntegerArg(args, "-epoch", builder::setEpoch);
        putIntegerArg(args, "-minCount", builder::setMinCount);
        putIntegerArg(args, "-minCountLabel", builder::setMinCountLabel);
        putIntegerArg(args, "-neg", builder::setNeg);
        putIntegerArg(args, "-wordNgrams", builder::setWordNgrams);
        putIntegerArg(args, "-bucket", builder::setBucket);
        putIntegerArg(args, "-minn", builder::setMinN);
        putIntegerArg(args, "-maxn", builder::setMaxN);
        putIntegerArg(args, "-thread", builder::setThread);
        putIntegerArg(args, "-cutoff", builder::setCutOff);
        putIntegerArg(args, "-dsub", builder::setDSub);

        putDoubleArg(args, "-lr", builder::setLR);
        putDoubleArg(args, "-t", builder::setSamplingThreshold);

        putBooleanArg(args, "-qnorm", builder::setQNorm);
        putBooleanArg(args, "-qout", builder::setQOut);

        putStringArg(args, "-label", builder::setLabel);

        if (args.containsKey("-loss")) {
            builder.setLossName(Args.LossName.fromName(args.get("-loss")));
        }

        return builder.build();
    }

    /**
     * Parses an array to Map
     * Example: "cbow -thread 4 -dim 128 -ws 5 -epoch 10 -minCount 5 -input %s -output %s" =>
     * "[cbow=null, -thread=4, -dim=128, -ws=5, -epoch=10, -minCount=5, -input=%s, -output=%s]"
     *
     * @param input array of strings
     * @return Map of strings as keys and values
     * @throws IllegalArgumentException if input is wrong or help is requested
     */
    public static Map<String, String> toMap(String... input) throws IllegalArgumentException {
        Map<String, String> res = new LinkedHashMap<>();
        for (int i = 0; i < input.length; i++) {
            String key = input[i];
            String value = null;
            if (key.startsWith("-")) {
                value = i == input.length - 1 || input[i + 1].startsWith("-") ? Boolean.TRUE.toString() : input[++i];
            }
            res.put(key, value);
        }
        if (res.containsKey("-h")) {
            throw Usage.ARGS.toException("Here is the help! Usage:");
        }
        return res;
    }

    private static void putStringArg(Map<String, String> map, String key, Consumer<String> setter) {
        if (!map.containsKey(key)) return;
        setter.accept(Objects.requireNonNull(map.get(key), "Null value for " + key));
    }

    private static void putIntegerArg(Map<String, String> map, String key, IntConsumer setter) {
        if (!map.containsKey(key)) return;
        String value = Objects.requireNonNull(map.get(key), "Null int value for " + key);
        try {
            setter.accept(Integer.parseInt(value));
        } catch (NumberFormatException n) {
            throw Usage.ARGS.toException("Wrong value for " + key + ": " + n.getMessage());
        }
    }

    private static void putDoubleArg(Map<String, String> map, String key, DoubleConsumer setter) {
        if (!map.containsKey(key)) return;
        String value = Objects.requireNonNull(map.get(key), "Null double value for " + key);
        try {
            setter.accept(Double.parseDouble(value));
        } catch (NumberFormatException n) {
            throw Usage.ARGS.toException("Wrong value for " + key + ": " + n.getMessage());
        }
    }

    private static void putBooleanArg(Map<String, String> map, String key, Consumer<Boolean> setter) {
        if (!map.containsKey(key)) return;
        String value = Objects.requireNonNull(map.get(key), "Null value for " + key);
        setter.accept(Boolean.parseBoolean(value));
    }

    /**
     * Usage helper.
     */
    private enum Usage {
        COMMON("usage: {fasttext} <command> <args>\n\n"
                + "The commands supported by fasttext are:\n\n"
                + "  supervised              train a supervised classifier\n"
                + "  quantize                quantize a model to reduce the memory usage\n"
                + "  test                    evaluate a supervised classifier\n"
                + "  predict                 predict most likely labels\n"
                + "  predict-prob            predict most likely labels with probabilities\n"
                + "  skipgram                train a skipgram model\n"
                + "  cbow                    train a cbow model\n"
                + "  print-word-vectors      print word vectors given a trained model\n"
                + "  print-sentence-vectors  print sentence vectors given a trained model\n"
                + "  nn                      query for nearest neighbors\n"
                + "  analogies               query for analogies\n"),
        TRAIN("usage: {fasttext} {supervised|skipgram|cbow} <args>"),
        QUANTIZE("usage: {fasttext} quantize <args>"),
        TEST("usage: {fasttext} test <model> <test-data> [<k>]\n\n"
                + "  <model>      model filename\n"
                + "  <test-data>  test data filename (if -, read from stdin)\n"
                + "  <k>          (optional; 1 by default) predict top k labels\n"),
        PREDICT("usage: {fasttext} predict[-prob] <model> <test-data> [<k>]\n\n"
                + "  <model>      model filename\n"
                + "  <test-data>  test data filename (if -, read from stdin)\n"
                + "  <k>          (optional; 1 by default) predict top k labels\n"),
        PRINT_WORD_VECTORS("usage: {fasttext} print-word-vectors <model>\n\n"
                + "  <model>      model filename\n"),
        PRINT_SENTENCE_VECTORS("usage: {fasttext} print-sentence-vectors <model>\n\n"
                + "  <model>      model filename\n"),
        PRINT_NGRAMS("usage: {fasttext} print-ngrams <model> <word>\n\n"
                + "  <model>      model filename\n"
                + "  <word>       word to print\n"),
        NN("usage: {fasttext} nn <model> <k>\n\n"
                + "  <model>      model filename\n"
                + "  <k>          (optional; 10 by default) predict top k labels\n"),
        ANALOGIES("usage: {fasttext} analogies <model> <k>\n\n"
                + "  <model>      model filename\n"
                + "  <k>          (optional; 10 by default) predict top k labels\n"),

        ARGS_BASIC_HELP("\nThe following arguments are mandatory:\n"
                + "  -input              training file uri\n"
                + "  -output             output file name\n"
                + "\nThe following arguments are optional:\n"
                + "  -verbose            verbosity level [integer]\n"),
        ARGS_DICTIONARY_HELP("\nThe following arguments for the dictionary are optional:\n"
                + "  -minCount           minimal number of word occurrences [integer]\n"
                + "  -minCountLabel      minimal number of label occurrences [integer]\n"
                + "  -wordNgrams         max length of word ngram [integer]\n"
                + "  -bucket             number of buckets [integer]\n"
                + "  -minn               min length of char ngram [integer]\n"
                + "  -maxn               max length of char ngram [integer]\n"
                + "  -t                  sampling threshold [double]\n"
                + "  -label              labels prefix [string]\n"),
        ARGS_TRAINING_HELP("\nThe following arguments for training are optional:\n"
                + "  -lr                 learning rate [double]\n"
                + "  -lrUpdateRate       change the rate of updates for the learning rate [integer]\n"
                + "  -dim                size of word vectors [integer]\n"
                + "  -ws                 size of the context window [integer]\n"
                + "  -epoch              number of epochs [integer]\n"
                + "  -neg                number of negatives sampled [integer]\n"
                + "  -loss               loss function {ns|hs|softmax} [string]\n"
                + "  -thread             number of threads [integer]\n"
                + "  -pretrainedVectors  pretrained word vectors for supervised learning [file uri]\n"
                + "  -saveOutput         whether output params should be saved [boolean]\n"),
        ARGS_QUANTIZATION_HELP("\nThe following arguments for quantization are optional:\n"
                + "  -cutoff             number of words and ngrams to retain [integer]\n"
                + "  -retrain            whether embeddings are finetuned if a cutoff is applied [boolean]\n"
                + "  -qnorm              whether the norm is quantized separately [boolean]\n"
                + "  -qout               whether the classifier is quantized [boolean\n"
                + "  -dsub               size of each sub-vector [integer]\n"),
        ARGS(ARGS_BASIC_HELP.message + ARGS_DICTIONARY_HELP.message + ARGS_TRAINING_HELP.message + ARGS_QUANTIZATION_HELP.message);

        private final String message;

        Usage(String msg) {
            this.message = msg;
        }

        public String getMessage() {
            return message.replace("{fasttext}", "java -jar fasttext.jar");
        }

        public IllegalArgumentException toException() {
            return createException(getMessage());
        }

        public IllegalArgumentException toException(String line) {
            return createException(line + "\n" + getMessage());
        }

        public IllegalArgumentException toException(String line, Usage extra) {
            return createException(line + "\n" + getMessage() + "\n" + extra.getMessage());
        }

        private static IllegalArgumentException createException(String msg) {
            return new WrongInputException(msg);
        }

        static class WrongInputException extends IllegalArgumentException {
            WrongInputException(String s) {
                super(s);
            }
        }
    }
}
