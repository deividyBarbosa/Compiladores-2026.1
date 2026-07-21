package dplusplus.semantico;

import java.util.ArrayList;
import java.util.List;

/**
 * Uma entrada da tabela de símbolos: representa uma classe, um objeto, uma
 * variável, uma constante, um parâmetro, um procedimento ou uma função.
 *
 * Cada {@link Simbolo} é um nó de uma lista encadeada dentro de um "balde"
 * (bucket) de uma {@link TabelaSimbolos} (ver aquela classe para o
 * encadeamento propriamente dito).
 */
public class Simbolo {

    public enum Categoria {
        CLASSE, OBJETO, VARIAVEL, CONSTANTE, PARAMETRO, PROCEDIMENTO, FUNCAO
    }

    private final String nome;
    private final Categoria categoria;
    private Tipo tipo;
    private boolean inicializado;
    private int linha;
    private int coluna;

    /** Apenas para CLASSE: informações completas da classe (herança, membros). */
    private InfoClasse infoClasse;

    /** Apenas para PROCEDIMENTO/FUNCAO: tipos dos parâmetros, na ordem declarada. */
    private List<Tipo> tiposParametros = new ArrayList<>();

    /** Apenas para PROCEDIMENTO/FUNCAO: true se o método não possui corpo. */
    private boolean abstrato;

    /** Apenas para PROCEDIMENTO: true se marcado com '>>' (ponto de entrada). */
    private boolean pontoDeEntrada;

    /** Nome da classe onde o membro foi originalmente declarado (para membros herdados). */
    private String classeDeOrigem;

    public Simbolo(String nome, Categoria categoria, Tipo tipo, int linha, int coluna) {
        this.nome = nome;
        this.categoria = categoria;
        this.tipo = tipo;
        this.linha = linha;
        this.coluna = coluna;
        this.inicializado = false;
    }

    public String getNome() { return nome; }
    public Categoria getCategoria() { return categoria; }
    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }
    public boolean isInicializado() { return inicializado; }
    public void setInicializado(boolean inicializado) { this.inicializado = inicializado; }
    public int getLinha() { return linha; }
    public int getColuna() { return coluna; }

    public boolean isMetodo() {
        return categoria == Categoria.PROCEDIMENTO || categoria == Categoria.FUNCAO;
    }

    public boolean isVariavelOuObjeto() {
        return categoria == Categoria.OBJETO || categoria == Categoria.VARIAVEL
            || categoria == Categoria.CONSTANTE || categoria == Categoria.PARAMETRO;
    }

    public boolean isMutavel() {
        return categoria == Categoria.OBJETO || categoria == Categoria.VARIAVEL || categoria == Categoria.PARAMETRO;
    }

    public InfoClasse getInfoClasse() { return infoClasse; }
    public void setInfoClasse(InfoClasse infoClasse) { this.infoClasse = infoClasse; }

    public List<Tipo> getTiposParametros() { return tiposParametros; }
    public void setTiposParametros(List<Tipo> tiposParametros) { this.tiposParametros = tiposParametros; }

    public boolean isAbstrato() { return abstrato; }
    public void setAbstrato(boolean abstrato) { this.abstrato = abstrato; }

    public boolean isPontoDeEntrada() { return pontoDeEntrada; }
    public void setPontoDeEntrada(boolean pontoDeEntrada) { this.pontoDeEntrada = pontoDeEntrada; }

    public String getClasseDeOrigem() { return classeDeOrigem; }
    public void setClasseDeOrigem(String classeDeOrigem) { this.classeDeOrigem = classeDeOrigem; }

    /** Cria uma cópia deste símbolo, usada ao herdar membros de uma classe mãe. */
    public Simbolo copiar() {
        Simbolo copia = new Simbolo(nome, categoria, tipo, linha, coluna);
        copia.inicializado = this.inicializado;
        copia.infoClasse = this.infoClasse;
        copia.tiposParametros = new ArrayList<>(this.tiposParametros);
        copia.abstrato = this.abstrato;
        copia.pontoDeEntrada = this.pontoDeEntrada;
        copia.classeDeOrigem = this.classeDeOrigem;
        return copia;
    }

    public String assinatura() {
        StringBuilder sb = new StringBuilder();
        if (categoria == Categoria.FUNCAO) {
            sb.append("function ").append(tipo).append(' ').append(nome);
        } else if (categoria == Categoria.PROCEDIMENTO) {
            sb.append("procedure ").append(nome);
        } else {
            return nome;
        }
        sb.append('[');
        for (int i = 0; i < tiposParametros.size(); i++) {
            if (i > 0) sb.append(" | ");
            sb.append(tiposParametros.get(i));
        }
        sb.append(']');
        return sb.toString();
    }

    @Override
    public String toString() {
        return nome + ":" + categoria + (tipo != null ? "<" + tipo + ">" : "");
    }
}
