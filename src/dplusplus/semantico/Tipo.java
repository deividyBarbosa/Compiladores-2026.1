package dplusplus.semantico;

/**
 * Representação de um tipo semântico de D++.
 *
 * A linguagem possui dois tipos primitivos (number, answer), tipos de
 * classe (definidos pelo usuário, com suporte a herança/polimorfismo),
 * o pseudo-tipo VOID (retorno de procedimentos) e dois tipos especiais
 * usados apenas internamente pelo analisador:
 *
 *  - ERRO: "tipo coringa" atribuído a uma expressão quando um erro já foi
 *          reportado para ela. Ele é compatível com qualquer outro tipo,
 *          evitando que um único erro dispare uma cascata de novos erros.
 *  - PRIMITIVO_COMPATIVEL: tipo do valor retornado por Periphericals.capture[]
 *          e aceito por Periphericals.show[]. O enunciado pede que ambos
 *          sejam tratados como compatíveis com os tipos primitivos, então
 *          esse tipo "casa" tanto com number quanto com answer.
 */
public final class Tipo {

    public enum Categoria { NUMBER, ANSWER, CLASSE, VOID, PRIMITIVO_COMPATIVEL, ERRO }

    public static final Tipo NUMBER = new Tipo(Categoria.NUMBER, null);
    public static final Tipo ANSWER = new Tipo(Categoria.ANSWER, null);
    public static final Tipo VOID = new Tipo(Categoria.VOID, null);
    public static final Tipo ERRO = new Tipo(Categoria.ERRO, null);
    public static final Tipo PRIMITIVO_COMPATIVEL = new Tipo(Categoria.PRIMITIVO_COMPATIVEL, null);

    private final Categoria categoria;
    private final String nomeClasse;

    private Tipo(Categoria categoria, String nomeClasse) {
        this.categoria = categoria;
        this.nomeClasse = nomeClasse;
    }

    public static Tipo classe(String nomeClasse) {
        return new Tipo(Categoria.CLASSE, nomeClasse);
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public String getNomeClasse() {
        return nomeClasse;
    }

    public boolean isErro() {
        return categoria == Categoria.ERRO;
    }

    public boolean isClasse() {
        return categoria == Categoria.CLASSE;
    }

    public boolean isVoid() {
        return categoria == Categoria.VOID;
    }

    /** true se o tipo pode ser usado em um contexto que exige "number". */
    public boolean pareceNumber() {
        return categoria == Categoria.NUMBER || categoria == Categoria.PRIMITIVO_COMPATIVEL || categoria == Categoria.ERRO;
    }

    /** true se o tipo pode ser usado em um contexto que exige "answer". */
    public boolean pareceAnswer() {
        return categoria == Categoria.ANSWER || categoria == Categoria.PRIMITIVO_COMPATIVEL || categoria == Categoria.ERRO;
    }

    /**
     * Verifica se um valor do tipo {@code origem} pode ser atribuído/usado em
     * um contexto que espera o tipo {@code destino} (declaração de variável,
     * atribuição, passagem de parâmetro, retorno de função). Contempla
     * polimorfismo: uma classe filha é compatível com um tipo da classe mãe.
     */
    public static boolean atribuivel(Tipo destino, Tipo origem, TabelaClasses classes) {
        if (destino == null || origem == null) {
            return true;
        }
        if (destino.isErro() || origem.isErro()) {
            return true;
        }
        if (destino.categoria == Categoria.PRIMITIVO_COMPATIVEL || origem.categoria == Categoria.PRIMITIVO_COMPATIVEL) {
            Tipo outro = destino.categoria == Categoria.PRIMITIVO_COMPATIVEL ? origem : destino;
            return outro.categoria == Categoria.NUMBER
                || outro.categoria == Categoria.ANSWER
                || outro.categoria == Categoria.PRIMITIVO_COMPATIVEL;
        }
        if (destino.categoria != origem.categoria) {
            return false;
        }
        if (destino.categoria == Categoria.CLASSE) {
            return classes.ehSubtipoOuIgual(origem.nomeClasse, destino.nomeClasse);
        }
        return true;
    }

    /**
     * Tenta encontrar um tipo comum entre dois ramos de uma expressão (usado
     * no operador ternário). Para tipos de classe, retorna o ancestral comum
     * mais próximo (útil para permitir polimorfismo nos dois ramos).
     * Retorna {@code Tipo.ERRO} quando não há tipo comum.
     */
    public static Tipo unificar(Tipo a, Tipo b, TabelaClasses classes) {
        if (a.isErro() || b.isErro()) {
            return Tipo.ERRO;
        }
        if (atribuivel(a, b, classes)) {
            return a;
        }
        if (atribuivel(b, a, classes)) {
            return b;
        }
        if (a.isClasse() && b.isClasse()) {
            String ancestral = classes.ancestralComum(a.nomeClasse, b.nomeClasse);
            if (ancestral != null) {
                return Tipo.classe(ancestral);
            }
        }
        return Tipo.ERRO;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Tipo)) {
            return false;
        }
        Tipo outro = (Tipo) obj;
        return categoria == outro.categoria
            && (nomeClasse == null ? outro.nomeClasse == null : nomeClasse.equals(outro.nomeClasse));
    }

    @Override
    public int hashCode() {
        int resultado = categoria.hashCode();
        if (nomeClasse != null) {
            resultado = 31 * resultado + nomeClasse.hashCode();
        }
        return resultado;
    }

    @Override
    public String toString() {
        switch (categoria) {
            case NUMBER: return "number";
            case ANSWER: return "answer";
            case VOID: return "void";
            case CLASSE: return nomeClasse;
            case PRIMITIVO_COMPATIVEL: return "primitivo";
            case ERRO: default: return "<erro>";
        }
    }
}
