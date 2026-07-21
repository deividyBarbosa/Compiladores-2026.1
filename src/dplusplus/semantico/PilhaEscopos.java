package dplusplus.semantico;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Pilha de escopos. Cada escopo é uma {@link TabelaSimbolos} (uma hash table
 * própria). Entrar em uma classe, em um método ou em um bloco start/finish
 * empilha uma nova tabela; sair desses elementos desempilha.
 *
 * A busca de um identificador percorre a pilha do topo (escopo mais interno,
 * ex.: bloco atual) para a base (ex.: atributos/métodos da classe), o que
 * implementa o "escopo é o bloco onde a variável foi declarada" pedido na
 * especificação, junto com o acesso direto a atributos/métodos da própria
 * classe (e das suas ancestrais, já que a tabela de membros da classe é
 * construída "achatada" com herança — ver {@link InfoClasse}).
 */
public class PilhaEscopos {

    private final Deque<TabelaSimbolos> pilha = new ArrayDeque<>();

    /** Empilha um novo escopo vazio (bloco/método/parâmetros). */
    public TabelaSimbolos abrirEscopo() {
        TabelaSimbolos tabela = new TabelaSimbolos();
        pilha.push(tabela);
        return tabela;
    }

    /** Empilha um escopo já existente (ex.: a tabela de membros "achatada" da classe atual). */
    public void abrirEscopo(TabelaSimbolos tabelaExistente) {
        pilha.push(tabelaExistente);
    }

    public void fecharEscopo() {
        pilha.pop();
    }

    /** Insere no escopo mais interno (topo da pilha). */
    public boolean inserir(Simbolo simbolo) {
        return pilha.peek().inserir(simbolo);
    }

    /** true se já existe um símbolo com este nome no escopo mais interno (declaração duplicada). */
    public boolean declaradoNoEscopoAtual(String nome) {
        return pilha.peek().contemLocal(nome);
    }

    /** Busca do escopo mais interno para o mais externo; retorna null se não encontrado em nenhum. */
    public Simbolo buscar(String nome) {
        Iterator<TabelaSimbolos> it = pilha.iterator();
        while (it.hasNext()) {
            Simbolo achado = it.next().buscarLocal(nome);
            if (achado != null) {
                return achado;
            }
        }
        return null;
    }

    public boolean vazia() {
        return pilha.isEmpty();
    }

    public int profundidade() {
        return pilha.size();
    }
}
