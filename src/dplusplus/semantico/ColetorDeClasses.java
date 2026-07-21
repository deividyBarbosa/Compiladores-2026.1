package dplusplus.semantico;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dplusplus.node.*;
/**
 * Passo 1 da análise semântica: percorre a árvore UMA vez, sem usar o
 * DepthFirstAdapter, para registrar todas as classes do programa
 * (atributos e assinaturas de métodos) antes de checar o corpo de cada
 * método. Isso é necessário porque, em D++, uma classe pode referenciar
 * outra que só é declarada mais adiante no arquivo, e a herança precisa
 * ser resolvida em ordem topológica (mãe antes da filha) para "achatar"
 * corretamente os membros herdados — uma ordem que não corresponde,
 * necessariamente, à ordem de visita em profundidade da árvore sintática.
 * Por isso esse passo é implementado como uma travessia direta do objeto
 * (usando os getters gerados pelo SableCC), e não como um visitor; o
 * padrão Visitor/DepthFirstAdapter é usado no passo 2, em
 * {@link AnalisadorSemantico}, que já encontra a tabela de classes pronta.
 */
public class ColetorDeClasses {

    private final GerenciadorErros erros;
    private final TabelaClasses classes = new TabelaClasses();

    private final Map<String, ADefClasseDefClasse> declaracoes = new LinkedHashMap<>();
    private final Map<String, String> paiDeclarado = new HashMap<>();
    private final Set<String> emProcessamento = new HashSet<>();
    private final Set<String> processadas = new HashSet<>();

    private int contadorPontosDeEntrada = 0;

    public ColetorDeClasses(GerenciadorErros erros) {
        this.erros = erros;
    }

    public TabelaClasses coletar(Start arvore) {
        AProgramaPrograma programa = (AProgramaPrograma) arvore.getPPrograma();

        registrarNomesDeClasses(programa);
        registrarHeranca(programa);
        for (String nome : new ArrayList<>(declaracoes.keySet())) {
            resolverClasse(nome);
        }
        validarPontoDeEntrada();

        return classes;
    }

    private void registrarNomesDeClasses(AProgramaPrograma programa) {
        for (PDefClasse pd : programa.getClasses()) {
            ADefClasseDefClasse decl = (ADefClasseDefClasse) pd;
            String nome = ConversorTipos.nomeDaClasse(decl.getTipoClasse());
            if (classes.existeClasse(nome)) {
                erros.erro(ConversorTipos.tokenDaClasse(decl.getTipoClasse()),
                    "Classe '" + nome + "' já foi declarada anteriormente.");
                continue;
            }
            declaracoes.put(nome, decl);
            classes.registrar(new InfoClasse(nome, decl));
        }
    }

    private void registrarHeranca(AProgramaPrograma programa) {
        for (PRelacao pr : programa.getRelacoes()) {
            ARelacaoRelacao r = (ARelacaoRelacao) pr;
            String filho = ConversorTipos.nomeDaClasse(r.getFilho());
            String pai = ConversorTipos.nomeDaClasse(r.getPai());

            if (!declaracoes.containsKey(filho)) {
                erros.erro(ConversorTipos.tokenDaClasse(r.getFilho()),
                    "Classe '" + filho + "' não foi declarada com 'family ... start ... finish'.");
                continue;
            }
            if (!declaracoes.containsKey(pai) && !classes.existeClasse(pai)) {
                erros.erro(ConversorTipos.tokenDaClasse(r.getPai()),
                    "Classe mãe '" + pai + "' não foi declarada.");
                continue;
            }
            if (paiDeclarado.containsKey(filho)) {
                erros.erro(ConversorTipos.tokenDaClasse(r.getFilho()),
                    "Classe '" + filho + "' já possui uma classe mãe declarada ('"
                        + paiDeclarado.get(filho) + "'); só é permitida herança simples.");
                continue;
            }
            paiDeclarado.put(filho, pai);
        }
    }

    private InfoClasse resolverClasse(String nome) {
        InfoClasse info = classes.buscar(nome);
        if (info == null || info.isPredefinida()) {
            return info;
        }
        if (processadas.contains(nome)) {
            return info;
        }

        ADefClasseDefClasse decl = declaracoes.get(nome);

        if (emProcessamento.contains(nome)) {
            erros.erro(ConversorTipos.tokenDaClasse(decl.getTipoClasse()),
                "Ciclo de herança detectado envolvendo a classe '" + nome + "'.");
            info.setPai(classes.getRoot());
            processadas.add(nome);
            return info;
        }
        emProcessamento.add(nome);

        String nomePai = paiDeclarado.getOrDefault(nome, TabelaClasses.NOME_ROOT);
        InfoClasse pai;
        if (nomePai.equals(nome)) {
            erros.erro(ConversorTipos.tokenDaClasse(decl.getTipoClasse()),
                "Classe '" + nome + "' não pode derivar de si mesma.");
            pai = classes.getRoot();
        } else {
            pai = resolverClasse(nomePai);
            if (pai == null) {
                pai = classes.getRoot();
            }
        }
        info.setPai(pai);

        for (Simbolo herdado : pai.getTabelaMembros().todosOsSimbolos()) {
            info.getTabelaMembros().inserirOuSubstituir(herdado.copiar());
        }

        processarAtributos(info, decl);
        processarMetodos(info, decl);

        emProcessamento.remove(nome);
        processadas.add(nome);
        return info;
    }

    private void processarAtributos(InfoClasse info, ADefClasseDefClasse decl) {
        for (PAtributosAlt pa : decl.getAtributos()) {
            if (pa instanceof AObjetoAtributosAlt) {
                AObjetoAtributosAlt o = (AObjetoAtributosAlt) pa;
                String nomeClasseAtributo = ConversorTipos.nomeDaClasse(o.getTipoClasse());
                Tipo tipo;
                if (!classes.existeClasse(nomeClasseAtributo)) {
                    erros.erro(ConversorTipos.tokenDaClasse(o.getTipoClasse()),
                        "Classe '" + nomeClasseAtributo + "' não foi declarada.");
                    tipo = Tipo.ERRO;
                } else {
                    tipo = Tipo.classe(nomeClasseAtributo);
                }
                Simbolo s = new Simbolo(o.getId().getText(), Simbolo.Categoria.OBJETO, tipo,
                    o.getId().getLine(), o.getId().getPos());
                s.setClasseDeOrigem(info.getNome());
                // Modelo adotado: um atributo 'object' é auto-instanciado junto com a
                // instância da classe (não existe operador 'new' em D++), logo já nasce
                // inicializado. Ver relatório da Etapa 5 para a justificativa.
                s.setInicializado(true);
                inserirAtributo(info, s, o.getId());
            } else if (pa instanceof AVariavelAtributosAlt) {
                AVariavelAtributosAlt v = (AVariavelAtributosAlt) pa;
                Tipo tipo = ConversorTipos.deTipoPrimitivo(v.getTipoPrimitivo());
                Simbolo s = new Simbolo(v.getId().getText(), Simbolo.Categoria.VARIAVEL, tipo,
                    v.getId().getLine(), v.getId().getPos());
                s.setClasseDeOrigem(info.getNome());
                s.setInicializado(true); // a gramática exige inicializador em toda declaração
                inserirAtributo(info, s, v.getId());
            } else if (pa instanceof AConstanteAtributosAlt) {
                AConstanteAtributosAlt c = (AConstanteAtributosAlt) pa;
                Tipo tipo = ConversorTipos.deTipoPrimitivo(c.getTipoPrimitivo());
                Simbolo s = new Simbolo(c.getId().getText(), Simbolo.Categoria.CONSTANTE, tipo,
                    c.getId().getLine(), c.getId().getPos());
                s.setClasseDeOrigem(info.getNome());
                s.setInicializado(true);
                inserirAtributo(info, s, c.getId());
            }
        }
    }

    private void inserirAtributo(InfoClasse info, Simbolo novo, Token tokenNome) {
        Simbolo existente = info.getTabelaMembros().buscarLocal(novo.getNome());
        if (existente == null) {
            info.getTabelaMembros().inserirOuSubstituir(novo);
            return;
        }
        if (info.getNome().equals(existente.getClasseDeOrigem())) {
            erros.erro(tokenNome, "'" + novo.getNome() + "' já foi declarado na classe '" + info.getNome() + "'.");
        } else {
            erros.erro(tokenNome, "'" + novo.getNome() + "' colide com um membro herdado de '"
                + existente.getClasseDeOrigem() + "'.");
        }
    }

    private void processarMetodos(InfoClasse info, ADefClasseDefClasse decl) {
        for (PMetodosAlt pm : decl.getMetodos()) {
            if (pm instanceof AProcedimentoMetodosAlt) {
                AProcedimentoMetodosAlt p = (AProcedimentoMetodosAlt) pm;
                AProcedureHeaderProcedureHeader header = (AProcedureHeaderProcedureHeader) p.getHeader();

                Simbolo s = new Simbolo(header.getId().getText(), Simbolo.Categoria.PROCEDIMENTO, Tipo.VOID,
                    header.getId().getLine(), header.getId().getPos());
                s.setTiposParametros(tiposDosParametros(header.getParams()));
                s.setAbstrato(p.getCorpo() == null);
                s.setClasseDeOrigem(info.getNome());
                s.setInicializado(true);

                boolean marcado = p.getOpEntry() != null;
                s.setPontoDeEntrada(marcado);
                if (marcado) {
                    contadorPontosDeEntrada++;
                    classes.setPontoDeEntrada(info, s);
                    if (s.isAbstrato()) {
                        erros.erro(header.getId(), "O ponto de entrada ('>>') não pode ser um procedimento abstrato.");
                    }
                }

                inserirOuSobrescreverMetodo(info, s, header.getId());
            } else if (pm instanceof AFuncaoMetodosAlt) {
                AFuncaoMetodosAlt f = (AFuncaoMetodosAlt) pm;
                AFunctionHeaderFunctionHeader header = (AFunctionHeaderFunctionHeader) f.getHeader();

                Tipo retorno = ConversorTipos.deTipo(header.getTipo(), classes, erros);
                Simbolo s = new Simbolo(header.getId().getText(), Simbolo.Categoria.FUNCAO, retorno,
                    header.getId().getLine(), header.getId().getPos());
                s.setTiposParametros(tiposDosParametros(header.getParams()));
                s.setAbstrato(f.getCorpo() == null);
                s.setClasseDeOrigem(info.getNome());
                s.setInicializado(true);

                inserirOuSobrescreverMetodo(info, s, header.getId());
            }
        }
    }

    private List<Tipo> tiposDosParametros(List<PParametro> params) {
        List<Tipo> tipos = new ArrayList<>();
        Set<String> nomes = new HashSet<>();
        for (PParametro pp : params) {
            AParametroParametro par = (AParametroParametro) pp;
            Tipo tipo = ConversorTipos.deTipo(par.getTipo(), classes, erros);
            tipos.add(tipo);
            TId idParam = par.getId();
            if (!nomes.add(idParam.getText())) {
                erros.erro(idParam, "Parâmetro '" + idParam.getText() + "' repetido na lista de parâmetros.");
            }
        }
        return tipos;
    }

    private void inserirOuSobrescreverMetodo(InfoClasse info, Simbolo novo, Token tokenNome) {
        Simbolo existente = info.getTabelaMembros().buscarLocal(novo.getNome());
        if (existente != null) {
            if (info.getNome().equals(existente.getClasseDeOrigem())) {
                erros.erro(tokenNome, "Método '" + novo.getNome() + "' já foi declarado na classe '" + info.getNome() + "'.");
                return;
            }
            if (existente.getCategoria() != novo.getCategoria()
                || !mesmosParametros(existente.getTiposParametros(), novo.getTiposParametros())
                || !retornoCompativelComSobrescrita(existente, novo)) {
                erros.erro(tokenNome, "Assinatura de '" + novo.getNome()
                    + "' incompatível com a versão herdada de '" + existente.getClasseDeOrigem()
                    + "' (esperado: " + existente.assinatura() + "). D++ não permite sobrecarga.");
            }
        }
        info.getTabelaMembros().inserirOuSubstituir(novo);
    }

    private boolean mesmosParametros(List<Tipo> a, List<Tipo> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** Permite retorno covariante em funções sobrescritas (polimorfismo): o novo retorno pode ser um subtipo do herdado. */
    private boolean retornoCompativelComSobrescrita(Simbolo herdado, Simbolo novo) {
        if (herdado.getCategoria() == Simbolo.Categoria.PROCEDIMENTO) {
            return true;
        }
        return Tipo.atribuivel(herdado.getTipo(), novo.getTipo(), classes);
    }

    private void validarPontoDeEntrada() {
        if (contadorPontosDeEntrada == 0) {
            erros.erro(0, 0, "Nenhum procedimento foi marcado como ponto de entrada ('>>'). "
                + "Todo programa D++ precisa de exatamente um.");
        } else if (contadorPontosDeEntrada > 1) {
            erros.erro(0, 0, "Mais de um procedimento foi marcado como ponto de entrada ('>>'); "
                + "apenas um é permitido por programa.");
        }
        InfoClasse classeEntrada = classes.getClasseDoPontoDeEntrada();
        if (classeEntrada != null && classeEntrada.isAbstrata()) {
            erros.erro(0, 0, "A classe '" + classeEntrada.getNome()
                + "', que contém o ponto de entrada, é abstrata e não pode ser instanciada automaticamente.");
        }
    }
}
