/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import scala.collection.JavaConverters._
import org.apache.lucene.analysis.CharArraySet

/**
  * List of stopwords taken from page: http://www.ranks.nl/stopwords
  *
  * author: Heitor Barbieri
  * date: 20170102
  **/
object Stopwords {
  def getStopwords: CharArraySet =
                           new CharArraySet(asJavaCollection[String](All), true)

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
"how", "howbeit", "however", "hundred", "i", "id", "ie", "if", "im", "immediate",
"immediately", "importance", "important", "in", "inc", "indeed", "index",
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
"okay", "old", "omitted", "on", "once", "one", "ones", "only", "onto", "or", "ord", "other",
"others", "otherwise", "ought", "our", "ours", "ourselves", "out", "outside", "over",
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
"substantially", "successfully", "such", "sufficiently", "suggest", "sup", "sure",
"t", "take", "taken", "taking", "tell", "tends", "th", "than", "thank", "thanks",
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
    "algún", "alguna", "algunas", "alguno", "algunos", "ambos", "ampleamos", "ante",
    "antes", "aquel", "aquellas", "aquellos", "aqui", "arriba", "atras", "bajo",
    "bastante", "bien", "cada", "cierta", "ciertas", "cierto", "ciertos", "como",
    "con", "conseguimos", "conseguir", "consigo", "consigue", "consiguen",
    "consigues", "cual", "cuando", "dentro", "desde", "donde", "dos", "el", "ellas",
    "ellos", "empleais", "emplean", "emplear", "empleas", "empleo", "en", "encima",
    "entonces", "entre", "era", "eramos", "eran", "eras", "eres", "es", "esta",
    "estaba", "estado", "estais", "estamos", "estan", "estoy", "fin", "fue",
    "fueron", "fui", "fuimos", "gueno", "ha", "hace", "haceis", "hacemos", "hacen",
    "hacer", "haces", "hago", "incluso", "intenta", "intentais", "intentamos",
    "intentan", "intentar", "intentas", "intento", "ir", "la", "largo", "las", "lo",
    "los", "mientras", "mio", "modo", "muchos", "muy", "nos", "nosotros", "otro",
    "para", "pero", "podeis", "podemos", "poder", "podria", "podriais", "podriamos",
    "podrian", "podrias", "por", "porque", "por qué", "primero 	", "puede",
    "pueden", "puedo", "quien", "sabe", "sabeis", "sabemos", "saben", "saber",
    "sabes", "ser", "si", "siendo", "sin", "sobre", "sois", "solamente", "solo",
    "somos", "soy", "su", "sus", "también", "teneis", "tenemos", "tener", "tengo",
    "tiempo", "tiene", "tienen", "todo", "trabaja", "trabajais", "trabajamos",
    "trabajan", "trabajar", "trabajas", "trabajo", "tras", "tuyo", "ultimo",
    "un", "una", "unas", "uno", "unos", "usa", "usais", "usamos", "usan", "usar",
    "usas", "uso", "va", "vais", "valor", "vamos", "van", "vaya", "verdad",
    "verdadera", "verdadero", "vosotras", "vosotros", "voy", "yo"
  ).map(Tools.uniformString)

  val Portuguese: Set[String] = Set[String](
    "acerca", "agora", "algumas", "alguns", "ali", "ambos", "antes", "apontar",
    "aquela", "aquelas", "aquele", "aqueles", "aqui", "atrás", "bem", "bom",
    "cada", "caminho", "cima", "com", "como", "comprido", "conhecido",
    "corrente", "das", "de", "debaixo", "dentro", "desde", "desligado", "deve",
    "devem", "deverá", "direita", "diz", "dizer", "dois", "dos", "e", "é",
    "ela", "ele", "eles", "em", "enquanto", "então", "está", "estado", "estão",
    "estar", "estará", "este", "estes", "esteve", "estive", "estivemos",
    "estiveram", "eu", "fará", "faz", "fazer", "fazia", "fez", "fim", "foi",
    "fora", "horas", "iniciar", "inicio", "ir", "irá", "ista", "iste", "isto",
    "ligado", "maioria", "maiorias", "mais", "mas", "mesmo", "meu", "muito",
    "muitos", "não", "nome", "nós", "nosso", "novo", "o", "onde", "os", "ou",
    "outro", "para", "parte", "pegar", "pelo", "pessoas", "pode", "poderá",
    "podia", "por", "porque", "povo", "promeiro", "qual", "qualquer", "quando",
    "quê", "quem", "quieto", "saber", "são", "sem", "ser", "seu", "sobre", "somente",
    "tal", "também", "tem", "têm", "tempo", "tenho", "tentar", "tentaram",
    "tente", "tentei", "teu", "teve", "tipo", "tive", "todos", "trabalhar",
    "trabalho", "tu", "último", "um", "uma", "umas", "uns", "usa", "usar",
    "valor", "veja", "ver", "verdade", "verdadeiro", "você"
  ).map(Tools.uniformString)

  val All: Set[String] = English ++ Spanish ++ Portuguese
}
