/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

//import scala.collection.JavaConverters._
import org.apache.lucene.analysis.CharArraySet

import scala.jdk.CollectionConverters._

/**
  * List of stopwords taken from page: http://www.ranks.nl/stopwords
  *
  * author: Heitor Barbieri
  * date: 20170102
  **/
object Stopwords {
  def getStopwords: CharArraySet =
                           new CharArraySet(All.asJavaCollection, true)

  val English: Set[String] = Set[String](
    "a", "able", "about", "above", "abst", "accordance", "according", "accordingly",
    "across", "act", "actually", "added", "adj", "affected", "affecting", "affects",
    "after", "afterwards", "again", "against", "ah", "all", "almost", "alone", "along",
    "already", "also", "although", "always", "am", "among", "amongst", "an", "and",
    "announce", "another", "any", "anybody", "anyhow", "anymore", "anyone",
    "anything", "anyway", "anyways", "anywhere", "apparently", "approximately",
    "are", "aren", "arent", "arise", "around", "as", "aside", "ask", "asking", "at",
    "auth", "available", "away", "awfully", "b", "back", "be", "became", "because",
    "become", "becomes", "becoming", "been", "before", "beforehand", "begin",
    "beginning", "beginnings", "begins", "behind", "being", "believe", "below",
    "beside", "besides", "between", "beyond", "biol", "both", "brief", "briefly",
    "but", "by", "c", "ca", "came", "can", "cannot", "can't", "cause", "causes",
    "certain", "certainly", "co", "com", "come", "comes", "contain", "containing",
    "contains", "could", "couldnt", "d", "date", "did", "didn't", "different", "do",
    "does", "doesn't", "doing", "done", "don't", "down", "downwards", "due", "during",
    "e", "each", "ed", "edu", "effect", "eg", "eight", "eighty", "either", "else",
    "elsewhere", "end", "ending", "enough", "especially", "et", "et-al", "etc", "even",
    "ever", "every", "everybody", "everyone", "everything", "everywhere", "ex",
    "except", "f", "far", "few", "ff", "fifth", "first", "five", "fix", "followed",
    "following", "follows", "for", "former", "formerly", "forth", "found", "four",
    "from", "further", "furthermore", "g", "gave", "get", "gets", "getting", "give",
    "given", "gives", "giving", "go", "goes", "gone", "got", "gotten", "h", "had",
    "happens", "hardly", "has", "hasn't", "have", "haven't", "having", "he", "hed",
    "hence", "her", "here", "hereafter", "hereby", "herein", "heres", "hereupon",
    "hers", "herself", "hes", "hi", "hid", "him", "himself", "his", "hither", "home",
    "how", "howbeit", "however", "http", "hundred", "i", "id", "ie", "if", "im",
    "immediate", "immediately", "importance", "important", "in", "inc", "indeed", "index",
    "information", "instead", "into", "invention", "inward", "is", "isnt", "it", "itd",
    "its", "itself", "j", "just", "k", "keep 	keeps", "kept", "kg",
    "km", "know", "known", "knows", "l", "largely", "last", "lately", "later", "latter",
    "latterly", "least", "less", "lest", "let", "lets", "like", "liked", "likely", "line",
    "little", "'ll", "look", "looking", "looks", "ltd", "m", "made", "mainly", "make",
    "makes", "many", "may", "maybe", "me", "mean", "means", "meantime", "meanwhile",
    "merely", "mg", "might", "million", "miss", "ml", "more", "moreover", "most", "mostly",
    "mr", "mrs", "much", "mug", "must", "my", "myself", "n", "na", "name", "namely", "nay", "nd",
    "near", "nearly", "necessarily", "necessary", "need", "needs", "neither", "never",
    "nevertheless", "new", "next", "nine", "ninety", "no", "nobody", "non", "none",
    "nonetheless", "noone", "nor", "normally", "nos", "not", "noted", "nothing", "now",
    "nowhere", "o", "obtain", "obtained", "obviously", "of", "off", "often", "oh", "ok",
    "okay", "old", "omitted", "on", "once", "one", "ones", "only", "onto", "or", "ord", "org",
    "other", "others", "otherwise", "ought", "our", "ours", "ourselves", "out", "outside", "over",
    "overall", "owing", "own", "p", "page", "pages", "part", "particular", "particularly",
    "past", "per", "perhaps", "placed", "please", "plus", "poorly", "possible", "possibly",
    "potentially", "pp", "predominantly", "present", "previously", "primarily",
    "probably", "promptly", "proud", "provides", "put", "q", "que", "quickly", "quite", "qv",
    "r", "ran", "rather", "rd", "re", "readily", "really", "recent", "recently", "ref", "refs",
    "regarding", "regardless", "regards", "related", "relatively", "research",
    "respectively", "resulted", "resulting", "results", "right", "run", "s", "said", "same",
    "saw", "say", "saying", "says", "sec", "section", "see", "seeing", "seem", "seemed",
    "seeming", "seems", "seen", "self", "selves", "sent", "seven", "several", "shall", "she",
    "shed", "she'll", "shes", "should", "shouldn't", "show", "showed", "shown", "showns",
    "shows", "significant", "significantly", "similar", "similarly", "since", "six",
    "slightly", "so", "some", "somebody", "somehow", "someone", "somethan", "something",
    "sometime", "sometimes", "somewhat", "somewhere", "soon", "sorry", "specifically",
    "specified", "specify", "specifying", "still", "stop", "strongly", "sub",
    "substantially", "successfully", "such", "sufficiently", "suggest", "sup", "sure", "t",
    "take", "taken", "taking", "tell", "tends", "th", "than", "thank", "thanks",
    "thanx", "that", "thats", "the", "their", "theirs", "them", "themselves",
    "then", "thence", "there", "thereafter", "thereby", "thered", "therefore", "therein",
    "thereof", "therere", "theres", "thereto", "thereupon", "these", "they", "theyd",
    "theyre", "think", "this", "those", "thou", "though", "thoughh", "thousand",
    "throug", "through", "throughout", "thru","thus", "til", "tip", "to", "together",
    "too", "took", "toward", "towards", "tried", "tries", "truly", "try", "trying",
    "ts", "twice", "two", "u", "un", "under", "unfortunately", "unless", "unlike",
    "unlikely", "until", "unto", "up", "upon", "ups", "us", "use", "used", "useful",
    "usefully", "usefulness", "uses", "using", "usually", "v", "value", "various",
    "very", "via", "viz", "vol", "vols", "vs", "w", "want", "wants", "was", "wasnt",
    "way", "we", "wed", "welcome", "went", "were", "werent", "what", "whatever",
    "whats", "when", "whence","whenever", "where", "whereafter", "whereas", "whereby",
    "wherein", "wheres", "whereupon", "wherever", "whether", "which", "while", "whim",
    "whither", "who", "whod", "whoever", "whole", "whom", "whomever", "whos", "whose",
    "why", "widely", "willing", "wish", "with", "within", "without", "wont", "words",
    "world", "would", "wouldnt", "www", "x", "y", "yes", "yet", "you", "youd", "your",
    "youre", "yours", "yourself", "yourselves", "z", "zero"
  ).map(Tools.uniformString)

  val Spanish: Set[String] = Set[String](
    "acá", "actualmente", "acuerdo", "adelante", "ademas", "además", "adrede",
    "afirmó", "agregó", "ahi", "ahí", "ahora", "al", "algo", "algún", "alguna",
    "algunas", "alguno", "algunos", "alli", "allí", "alrededor", "ambos",
    "ampleamos", "añadió", "antano", "antaño", "ante", "anterior", "antes",
    "apenas", "aproximadamente", "aquel", "aquél", "aquella", "aquélla",
    "aquellas", "aquéllas", "aquello", "aquellos", "aquéllos", "aqui", "aquí",
    "arriba", "arribaabajo", "as", "aseguró", "asi", "así", "atras", "aun",
    "aún", "aunque", "ayer", "b", "bajo", "bastante", "bien", "breve", "buen",
    "buena", "buenas", "bueno", "buenos", "c", "cada", "casi", "cerca",
    "cierta", "ciertas", "cierto", "ciertos", "claro", "comentó", "como",
    "cómo", "con", "conmigo", "conocer", "conseguimos", "conseguir",
    "considera", "consideró", "consigo", "consigue", "consiguen", "consigues",
    "contigo", "contra", "cosa", "cosas", "creo", "cual", "cuál", "cuales",
    "cuáles", "cualquier", "cuando", "cuándo", "cuanta", "cuánta", "cuantas",
    "cuántas", "cuanto", "cuánto", "cuantos", "cuántos", "cuatro", "cuenta",
    "da", "dado", "dan", "dar", "de", "debajo", "debe", "debemos", "deben",
    "debido", "decir", "dejó", "del", "delante", "demás", "demasiado",
    "dentro", "deprisa", "desde", "despacio", "despues", "después", "detras",
    "detrás", "dice", "dicen", "dicho", "dieron", "diferente", "diferentes",
    "dijeron", "dijo", "dio", "donde", "dónde", "dos", "durante", "ejemplo",
    "el", "él", "ella", "ellas", "ello", "ellos", "embargo", "empleais",
    "emplean", "emplear", "empleas", "empleo", "en", "encima", "encuentra",
    "enfrente", "enseguida", "entonces", "entre", "era", "erais", "eramos",
    "éramos", "eran", "eras", "eres", "es", "esa", "ésa", "esas", "ésas", "ese",
    "ése", "eso", "esos", "ésos", "esta", "está", "ésta", "estaba", "estabais",
    "estábamos", "estaban", "estabas", "estad", "estada", "estadas", "estado",
    "estados", "estais", "estáis", "estamos", "estan", "están", "estando", "estar",
    "estará", "estarán", "estarás", "estaré", "estaréis", "estaremos", "estaría",
    "estaríais", "estaríamos", "estarían", "estarías", "estas", "estás", "éstas",
    "este", "esté", "éste", "estéis", "estemos", "estén", "estés", "esto", "estos",
    "éstos", "estoy", "estuve", "estuviera", "estuvierais", "estuviéramos",
    "estuvieran", "estuvieras", "estuvieron", "estuviese", "estuvieseis",
    "estuviésemos", "estuviesen", "estuvieses", "estuvimos", "estuviste", "estuvisteis",
    "estuvo", "ex", "excepto", "existe", "existen", "explicó", "expresó", "fin",
    "final", "frente", "fue", "fuera", "fuerais", "fuéramos", "fueran", "fueras",
    "fueron", "fuese", "fueseis", "fuésemos", "fuesen", "fueses", "fui", "fuimos",
    "fuiste", "fuisteis", "general", "gran", "grandes", "gueno", "ha", "habéis",
    "haber", "habia", "había", "habíais", "habíamos", "habían", "habías", "habida",
    "habidas", "habido", "habidos", "habiendo", "habla", "hablan", "habrá", "habrán",
    "habrás", "habré", "habréis", "habremos", "habría", "habríais", "habríamos",
    "habrían", "habrías", "hace", "haceis", "hacemos", "hacen", "hacer", "hacerlo",
    "haces", "hacia", "haciendo", "hago", "han", "has", "hasta", "hay", "haya", "hayáis",
    "hayamos", "hayan", "hayas", "he", "hecho", "hemos", "hicieron", "hizo", "horas",
    "hoy", "hube", "hubiera", "hubierais", "hubiéramos", "hubieran", "hubieras",
    "hubieron", "hubiese", "hubieseis", "hubiésemos", "hubiesen", "hubieses", "hubimos",
    "hubiste", "hubisteis", "hubo", "igual", "incluso", "indicó", "informo", "informó",
    "intenta", "intentais", "intentamos", "intentan", "intentar", "intentas", "intento",
    "ir", "junto", "la", "lado", "largo", "las", "le", "lejos", "les", "llegó", "lleva",
    "llevar", "lo", "los", "luego", "lugar", "mal", "manera", "manifestó", "mas", "más",
    "mayor", "me", "mediante", "medio", "mejor", "mencionó", "menos", "menudo", "mi",
    "mí", "mia", "mía", "mias", "mías", "mientras", "mio", "mío", "mios", "míos", "mis",
    "misma", "mismas", "mismo", "mismos", "modo", "momento", "mucha", "muchas", "mucho",
    "muchos", "muy", "nada", "nadie", "ni", "ningún", "ninguna", "ningunas", "ninguno",
    "ningunos", "no", "nos", "nosotras", "nosotros", "nuestra", "nuestras", "nuestro",
    "nuestros", "nueva", "nuevas", "nuevo", "nuevos", "nunca", "ocho", "os", "otra",
    "otras", "otro", "otros", "para", "parece", "parte", "partir", "pasada", "pasado",
    "peor", "pero", "pesar", "poca", "pocas", "poco", "pocos", "podeis", "podemos",
    "poder", "podrá", "podrán", "podria", "podría", "podriais", "podriamos", "podrian",
    "podrían", "podrias", "poner", "por", "porque", "por qué", "posible", "primer",
    "primera", "primero", "primeros", "principalmente", "pronto", "propia", "propias",
    "propio", "propios", "proximo", "próximo", "próximos", "pudo", "pueda", "puede",
    "pueden", "puedo", "pues", "qeu", "que", "qué", "quedó", "queremos", "quien", "quién",
    "quienes", "quiénes", "quiere", "quiza", "quizá", "quizas", "quizás", "raras",
    "realizado", "realizar", "realizó", "repente", "respecto", "sabe", "sabeis", "sabemos",
    "saben", "saber", "sabes", "sal", "salvo", "se", "sé", "sea", "seáis", "seamos", "sean",
    "seas", "segun", "según", "segunda", "segundo", "seis", "señaló", "ser", "sera", "será",
    "serán", "serás", "seré", "seréis", "seremos", "sería", "seríais", "seríamos", "serían",
    "serías", "si", "sí", "sido", "siempre", "siendo", "siete", "sigue", "siguiente", "sin",
    "sino", "sobre", "sois", "sola", "solamente", "solas", "solo", "sólo", "solos", "somos",
    "son", "soy", "soyos", "su", "supuesto", "sus", "suya", "suyas", "suyo", "suyos", "tal",
    "tambien", "también", "tambíen", "tampoco", "tan", "tanto", "tarde", "te", "temprano",
    "tendrá", "tendrán", "tendrás", "tendré", "tendréis", "tendremos", "tendría", "tendríais",
    "tendríamos", "tendrían", "tendrías", "tened", "teneis", "tenéis", "tenemos", "tener",
    "tenga", "tengáis", "tengamos", "tengan", "tengas", "tengo", "tenía", "teníais",
    "teníamos", "tenían", "tenías", "tenida", "tenidas", "tenido", "tenidos", "teniendo",
    "tercera", "ti", "tiempo", "tiene", "tienen", "tienes", "toda", "todas", "todavia",
    "todavía", "todo", "todos", "total", "trabaja", "trabajais", "trabajamos", "trabajan",
    "trabajar", "trabajas", "trabajo", "tras", "trata", "través", "tres", "tu", "tú", "tus",
    "tuve", "tuviera", "tuvierais", "tuviéramos", "tuvieran", "tuvieras", "tuvieron", "tuviese",
    "tuvieseis", "tuviésemos", "tuviesen", "tuvieses", "tuvimos", "tuviste", "tuvisteis", "tuvo",
    "tuya", "tuyas", "tuyo", "tuyos", "última", "últimas", "ultimo", "último", "últimos", "un",
    "una", "unas", "uno", "unos", "usa", "usais", "usamos", "usan", "usar", "usas", "uso",
    "usted", "ustedes", "va", "vais", "valor", "vamos", "van", "varias", "varios", "vaya",
    "veces", "vemos", "ver", "verdad", "verdadera", "verdadero", "vez", "vosotras", "vosotros",
    "voy", "vuestra", "vuestras", "vuestro", "vuestros"
  ).map(Tools.uniformString)

  val Portuguese: Set[String] = Set[String](
    "a", "à", "acerca", "acordo", "adeus", "afirma", "afirmou", "agora", "aí",
    "ainda", "além", "algmas", "algo", "alguém", "algum", "alguma", "algumas",
    "alguns", "ali", "ambos", "ampla", "amplas", "amplo", "amplos", "ano",
    "anos", "ante", "antes", "ao", "aos", "apenas", "apoio", "apontar",
    "após", "aquela^", "aquelas", "aquele", "aqueles", "aqui", "aquilo", "área",
    "as", "às", "assim", "até", "atrás", "através", "baixo", "banco", "bastante",
    "bem", "boa", "boas", "bom", "bons", "breve", "cá", "cada", "caminho",
    "caso", "cento", "central", "centro", "cerca", "certamente", "certeza",
    "cima", "coisa", "coisas", "com", "como", "comprido", "conhecido", "conta",
    "contra", "contudo", "corrente", "da", "dá", "dão", "daquela", "daquelas",
    "daquele", "daqueles", "dar", "das", "de", "debaixo", "dela", "delas", "dele",
    "deles", "demais", "dentro", "depois", "desde", "desligado", "dessa", "dessas",
    "desse", "desses", "desta", "destas", "deste", "destes", "deve", "devem",
    "devendo", "dever", "deverá", "deverão", "deveria", "deveriam", "devia",
    "deviam", "dia", "diante", "direita", "disse", "disso", "disto", "dito", "diz",
    "dizem", "dizer", "do", "dois", "dos", "durante", "e", "é", "ela", "elas",
    "ele", "eles", "em", "embora", "enquanto", "então", "entre", "era", "eram",
    "éramos", "és", "essa", "essas", "esse", "esses", "esta", "está", "estado",
    "estados", "estamos", "estão", "estar", "estará", "estas", "estás", "estava",
    "estavam", "estávamos", "este", "esteja", "estejam", "estejamos", "estes",
    "esteve", "estive", "estivemos", "estiver", "estivera", "estiveram", "estivéramos",
    "estiverem", "estivermos", "estivesse", "estivessem", "estivéssemos", "estivestes",
    "estou", "etc", "eu", "faço", "fará", "fato", "favor", "faz", "fazeis", "fazem",
    "fazemos", "fazendo", "fazer", "fazes", "fazia", "feita", "feitas", "feito",
    "feitos", "fez", "ficou", "fim", "final", "foi", "fomos", "for", "fora", "foram",
    "fôramos", "forem", "forma", "formos", "fosse", "fossem", "fôssemos", "foste",
    "fostes", "from", "fui", "geral", "grande", "grandes", "há", "haja", "hajam",
    "hajamos", "hão", "havemos", "havia", "hei", "houve", "houvemos", "houver",
    "houvera", "houverá", "houveram", "houvéramos", "houverão", "houverei",
    "houverem", "houveremos", "houveria", "houveriam", "houveríamos", "houvermos",
    "houvesse", "houvessem", "houvéssemos", "iniciar", "inicio", "início", "ir",
    "irá", "isso", "ista", "iste", "isto", "já", "la", "lá", "lado", "lei", "lhe",
    "lhes", "ligado", "lo", "local", "logo", "longe", "lugar", "maior", "maioria",
    "maiorias", "mais", "mal", "mas", "máximo", "me", "média", "meio", "melhor",
    "menor", "menos", "mesma", "mesmas", "mesmo", "mesmos", "meu", "meus", "minha",
    "minhas", "momento", "muita", "muitas", "muito", "muitos", "na", "nada", "não",
    "naquela", "naquelas", "naquele", "naqueles", "nas", "nem", "nenhum", "nenhuma",
    "nessa", "nessas", "nesse", "nesses", "nesta", "nestas", "neste", "nestes",
    "ninguém", "nível", "no", "nome", "nos", "nós", "nossa", "nossas", "nosso",
    "nossos", "nova", "novas", "novo", "novos", "num", "numa", "número", "nunca",
    "o", "obra", "obrigada", "obrigado", "onde", "ontem", "os", "ou", "outra", "outras",
    "outro", "outros", "para", "parece", "parte", "partido", "partir", "paucas",
    "pegar", "pela", "pelas", "pelo", "pelos", "pequena", "pequenas", "pequeno",
    "pequenos", "per", "perante", "período", "perto", "pessoas", "pode", "pôde",
    "podem", "podendo", "poder", "poderá", "poderia", "poderiam", "podia", "podiam",
    "põe", "põem", "pois", "ponto", "pontos", "por", "porém", "porque", "porquê",
    "possível", "possivelmente", "posso", "pouca", "poucas", "pouco", "poucos",
    "povo", "produtos", "promeiro", "própria", "próprias", "próprio", "próprios",
    "próxima", "próximas", "próximo", "próximos", "pude", "puderam", "quais",
    "quáis", "qual", "qualquer", "quando", "quanto", "quantos", "quase", "que",
    "quê", "quem", "quer", "quereis", "querem", "queremas", "queres", "quero",
    "quieto", "real", "sabe", "sabem", "saber", "são", "se", "segundo", "sei",
    "seis", "seja", "sejam", "sejamos", "sem", "sempre", "sendo", "ser", "será",
    "serão", "serei", "seremos", "seria", "seriam", "seríamos", "seu", "seus",
    "si", "sido", "sim", "só", "sob", "sobre", "sois", "somente", "somos", "sou",
    "sua", "suas", "tal", "talvez", "também", "tampouco", "tanta", "tantas",
    "tanto", "tão", "tarde", "te", "tel", "tem", "tém", "têm", "temos", "tempo",
    "tendes", "tendo", "tenha", "tenham", "tenhamos", "tenho", "tens", "tentar",
    "tentaram", "tente", "tentei", "ter", "terá", "terão", "terceira", "terceiro",
    "terei", "teremos", "teria", "teriam", "teríamos", "teu", "teus", "teve", "ti",
    "tido", "tinha", "tinham", "tínhamos", "tipo", "tive", "tivemos", "tiver",
    "tivera", "tiveram", "tivéramos", "tiverem", "tivermos", "tivesse", "tivessem",
    "tivéssemos", "tiveste", "tivestes", "toda", "todas", "todavia", "todo",
    "todos", "trabalhar", "trabalho", "tu", "tua", "tuas", "tudo", "última",
    "últimas", "último", "últimos", "um", "uma", "umas", "uns", "us", "usa",
    "usar", "vai", "vais", "valor", "vão", "vários", "veja", "vem", "vêm",
    "vendo", "vens", "ver", "verdade", "verdadeiro", "vez", "vezes", "vida",
    "vindo", "vinte", "vir", "você", "vocês", "vos", "vós", "vossa", "vossas",
    "vosso", "vossos"
  ).map(Tools.uniformString)

  val All: Set[String] = English ++ Spanish ++ Portuguese
}
