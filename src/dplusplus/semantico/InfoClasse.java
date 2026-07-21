package dplusplus.semantico;

import java.util.ArrayList;
import java.util.List;

import dplusplus.node.ADefClasseDefClasse;

/**
 * Metadados completos de uma classe de D++: nome, classe mãe e a tabela de
 * membros (atributos + métodos) "achatada" — ou seja, já contendo tanto os
 * membros declarados na própria classe quanto os herdados de todas as
 * ancestrais (com os métodos sobrescritos substituindo a versão herdada).
 * Essa tabela de membros é, ela mesma, uma {@link TabelaSimbolos} (uma hash
 * table), portanto cada classe corresponde a um escopo próprio, como pede o
 * enunciado.
 */
public class InfoClasse {

    private final String nome;
    private final ADefClasseDefClasse declaracao;
    private InfoClasse pai;
    private final TabelaSimbolos tabelaMembros = new TabelaSimbolos();
    private final boolean predefinida;
    private final boolean raiz;

    public InfoClasse(String nome, ADefClasseDefClasse declaracao) {
        this(nome, declaracao, false, false);
    }

    public InfoClasse(String nome, ADefClasseDefClasse declaracao, boolean predefinida, boolean raiz) {
        this.nome = nome;
        this.declaracao = declaracao;
        this.predefinida = predefinida;
        this.raiz = raiz;
    }

    public String getNome() { return nome; }
    public ADefClasseDefClasse getDeclaracao() { return declaracao; }
    public InfoClasse getPai() { return pai; }
    public void setPai(InfoClasse pai) { this.pai = pai; }
    public TabelaSimbolos getTabelaMembros() { return tabelaMembros; }
    public boolean isPredefinida() { return predefinida; }
    public boolean isRaiz() { return raiz; }

    /** Uma classe é abstrata se ela (ou alguma ancestral) possui método abstrato ainda não implementado. */
    public boolean isAbstrata() {
        if (raiz) {
            return true;
        }
        for (Simbolo s : tabelaMembros.todosOsSimbolos()) {
            if (s.isMetodo() && s.isAbstrato()) {
                return true;
            }
        }
        return false;
    }

    /** Lista, em ordem alfabética, os métodos abstratos ainda pendentes (para mensagens de erro). */
    public List<String> metodosAbstratosPendentes() {
        List<String> lista = new ArrayList<>();
        for (Simbolo s : tabelaMembros.todosOsSimbolos()) {
            if (s.isMetodo() && s.isAbstrato()) {
                lista.add(s.assinatura());
            }
        }
        java.util.Collections.sort(lista);
        return lista;
    }

    @Override
    public String toString() {
        return nome;
    }
}
