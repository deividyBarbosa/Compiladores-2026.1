package dplusplus.semantico;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Registro global de classes de um programa D++. É, ela mesma, mais um
 * escopo implementado como {@link TabelaSimbolos} (a hash table de listas
 * encadeadas) — cada classe é guardada como um {@link Simbolo} de categoria
 * CLASSE que aponta para o seu {@link InfoClasse}.
 *
 * Já nasce com as duas classes que fazem parte da linguagem por definição:
 * Root (topo da hierarquia, não pode ser instanciada, sem atributos/métodos)
 * e Periphericals (classe pré-definida de E/S, com os métodos show[] e
 * capture[]).
 */
public class TabelaClasses {

    public static final String NOME_ROOT = "Root";
    public static final String NOME_PERIPHERICALS = "Periphericals";

    private final TabelaSimbolos escopoGlobal = new TabelaSimbolos();
    private final List<InfoClasse> ordemDeclaracao = new ArrayList<>();

    private final InfoClasse root;
    private final InfoClasse periphericals;

    private InfoClasse classeDoPontoDeEntrada;
    private Simbolo metodoPontoDeEntrada;

    public TabelaClasses() {
        root = new InfoClasse(NOME_ROOT, null, true, true);
        registrar(root);

        periphericals = new InfoClasse(NOME_PERIPHERICALS, null, true, false);
        periphericals.setPai(root);

        Simbolo show = new Simbolo("show", Simbolo.Categoria.PROCEDIMENTO, Tipo.VOID, 0, 0);
        show.setTiposParametros(java.util.Collections.singletonList(Tipo.PRIMITIVO_COMPATIVEL));
        show.setClasseDeOrigem(NOME_PERIPHERICALS);
        periphericals.getTabelaMembros().inserirOuSubstituir(show);

        Simbolo capture = new Simbolo("capture", Simbolo.Categoria.FUNCAO, Tipo.PRIMITIVO_COMPATIVEL, 0, 0);
        capture.setTiposParametros(java.util.Collections.emptyList());
        capture.setClasseDeOrigem(NOME_PERIPHERICALS);
        periphericals.getTabelaMembros().inserirOuSubstituir(capture);

        registrar(periphericals);
    }

    public InfoClasse getRoot() { return root; }
    public InfoClasse getPeriphericals() { return periphericals; }

    public boolean existeClasse(String nome) {
        return escopoGlobal.contemLocal(nome);
    }

    public InfoClasse buscar(String nome) {
        Simbolo s = escopoGlobal.buscarLocal(nome);
        return s == null ? null : s.getInfoClasse();
    }

    public void registrar(InfoClasse info) {
        Simbolo s = new Simbolo(info.getNome(), Simbolo.Categoria.CLASSE, null, 0, 0);
        s.setInfoClasse(info);
        s.setInicializado(true);
        escopoGlobal.inserirOuSubstituir(s);
        ordemDeclaracao.add(info);
    }

    public List<InfoClasse> todas() {
        return ordemDeclaracao;
    }

    public void setPontoDeEntrada(InfoClasse classe, Simbolo metodo) {
        this.classeDoPontoDeEntrada = classe;
        this.metodoPontoDeEntrada = metodo;
    }

    public InfoClasse getClasseDoPontoDeEntrada() { return classeDoPontoDeEntrada; }
    public Simbolo getMetodoPontoDeEntrada() { return metodoPontoDeEntrada; }

    /** true se {@code filho} é a própria classe {@code ancestral} ou descende dela (direta ou indiretamente). */
    public boolean ehSubtipoOuIgual(String filho, String ancestral) {
        if (filho == null || ancestral == null) {
            return false;
        }
        InfoClasse atual = buscar(filho);
        Set<String> visitadas = new HashSet<>();
        while (atual != null && visitadas.add(atual.getNome())) {
            if (atual.getNome().equals(ancestral)) {
                return true;
            }
            atual = atual.getPai();
        }
        return false;
    }

    /** Ancestral comum mais próximo entre duas classes (incluindo elas mesmas), ou null se não houver (nunca deveria acontecer, já que toda classe descende de Root). */
    public String ancestralComum(String a, String b) {
        Set<String> cadeiaA = new HashSet<>();
        InfoClasse atual = buscar(a);
        Set<String> visitadas = new HashSet<>();
        while (atual != null && visitadas.add(atual.getNome())) {
            cadeiaA.add(atual.getNome());
            atual = atual.getPai();
        }
        atual = buscar(b);
        visitadas.clear();
        while (atual != null && visitadas.add(atual.getNome())) {
            if (cadeiaA.contains(atual.getNome())) {
                return atual.getNome();
            }
            atual = atual.getPai();
        }
        return null;
    }
}
