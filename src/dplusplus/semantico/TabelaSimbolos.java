package dplusplus.semantico;

/**
 * Tabela de símbolos de UM escopo, implementada como uma hash table cujos
 * baldes (buckets) são listas encadeadas (tratamento de colisão por
 * encaixamento/chaining) — exatamente a estrutura pedida no enunciado da
 * Etapa 5. Cada escopo da linguagem (classe, corpo de método, bloco
 * start/finish) possui a sua própria instância desta classe; o
 * encadeamento *entre* escopos (variável não achada aqui -> procurar no
 * escopo mais externo) é responsabilidade de {@link PilhaEscopos}, não
 * desta classe.
 *
 * A função de hash é a tradução direta, para Java, do código em C fornecido
 * na especificação da etapa (a mesma função de hash usada no símbolo da
 * "TINY machine" do livro do Louden, Compiler Construction):
 *
 * <pre>
 * #define SIZE ...
 * #define SHIFT 4
 *
 * int hash(char* key)
 * {
 *     int temp = 0;
 *     int i = 0;
 *     while (key[i] != '\0')
 *     {
 *         temp = ((temp &lt;&lt; SHIFT) + key[i]) % SIZE;
 *         ++i;
 *     }
 *     return temp;
 * }
 * </pre>
 */
public class TabelaSimbolos {

    /** Tamanho padrão da tabela (número de baldes/listas encadeadas). */
    public static final int TAMANHO_PADRAO = 211;

    private static final int SHIFT = 4;

    /** Cada posição do vetor é a cabeça de uma lista encadeada (um "balde"). */
    private final No[] baldes;
    private final int tamanho;

    /** Nó de uma lista encadeada dentro de um balde da hash table. */
    private static final class No {
        final Simbolo simbolo;
        No proximo;

        No(Simbolo simbolo, No proximo) {
            this.simbolo = simbolo;
            this.proximo = proximo;
        }
    }

    public TabelaSimbolos() {
        this(TAMANHO_PADRAO);
    }

    public TabelaSimbolos(int tamanho) {
        this.tamanho = tamanho;
        this.baldes = new No[tamanho];
    }

    /**
     * Tradução literal da função hash() em C: desloca o acumulador
     * {@code SHIFT} bits para a esquerda, soma o código do caractere atual
     * e aplica o módulo pelo tamanho da tabela a cada iteração.
     */
    private int hash(String chave) {
        int temp = 0;
        for (int i = 0; i < chave.length(); i++) {
            temp = ((temp << SHIFT) + chave.charAt(i)) % tamanho;
        }
        return temp;
    }

    /**
     * Insere um símbolo neste escopo. Retorna {@code false} (sem inserir)
     * se já existir um símbolo com o mesmo nome NESTE escopo — quem chama
     * decide o que fazer (normalmente reportar "identificador já
     * declarado neste escopo").
     */
    public boolean inserir(Simbolo simbolo) {
        if (buscarLocal(simbolo.getNome()) != null) {
            return false;
        }
        int indice = hash(simbolo.getNome());
        baldes[indice] = new No(simbolo, baldes[indice]);
        return true;
    }

    /** Insere sempre, mesmo substituindo/duplicando (usado ao herdar/sobrescrever membros). */
    public void inserirOuSubstituir(Simbolo simbolo) {
        removerLocal(simbolo.getNome());
        int indice = hash(simbolo.getNome());
        baldes[indice] = new No(simbolo, baldes[indice]);
    }

    private void removerLocal(String nome) {
        int indice = hash(nome);
        No atual = baldes[indice];
        No anterior = null;
        while (atual != null) {
            if (atual.simbolo.getNome().equals(nome)) {
                if (anterior == null) {
                    baldes[indice] = atual.proximo;
                } else {
                    anterior.proximo = atual.proximo;
                }
                return;
            }
            anterior = atual;
            atual = atual.proximo;
        }
    }

    /** Busca um símbolo apenas nesta tabela (neste escopo), percorrendo a lista encadeada do balde. */
    public Simbolo buscarLocal(String nome) {
        int indice = hash(nome);
        No atual = baldes[indice];
        while (atual != null) {
            if (atual.simbolo.getNome().equals(nome)) {
                return atual.simbolo;
            }
            atual = atual.proximo;
        }
        return null;
    }

    public boolean contemLocal(String nome) {
        return buscarLocal(nome) != null;
    }

    public java.util.List<Simbolo> todosOsSimbolos() {
        java.util.List<Simbolo> lista = new java.util.ArrayList<>();
        for (No cabeca : baldes) {
            for (No atual = cabeca; atual != null; atual = atual.proximo) {
                lista.add(atual.simbolo);
            }
        }
        return lista;
    }
}
