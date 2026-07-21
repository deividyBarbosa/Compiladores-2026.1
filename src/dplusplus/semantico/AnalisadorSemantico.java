package dplusplus.semantico;

import java.util.IdentityHashMap;
import java.util.List;

import dplusplus.analysis.DepthFirstAdapter;
import dplusplus.node.*;

/**
 * Passo 2 da análise semântica de D++: um visitor (padrão de projeto
 * Visitor) implementado como subclasse de {@link DepthFirstAdapter}, a
 * classe gerada pelo SableCC que percorre a árvore sintática abstrata em
 * profundidade, chamando {@code inXxx}/{@code outXxx} para cada tipo de nó.
 *
 * Este passo assume que {@link ColetorDeClasses} (passo 1) já construiu a
 * {@link TabelaClasses} completa (todas as classes, com atributos, métodos
 * e herança já resolvidos), e usa essa informação para:
 *
 * <ul>
 *   <li>controlar os escopos de bloco/método/classe empilhando e
 *       desempilhando {@link TabelaSimbolos} através de {@link PilhaEscopos}
 *       exatamente quando entra/sai de um {start...finish} ou de um método;</li>
 *   <li>checar se cada identificador usado foi declarado e está em um
 *       escopo alcançável a partir do ponto de uso (variável local, atributo
 *       da própria classe/ancestral, ou membro de outro objeto acessado via
 *       {@code ->});</li>
 *   <li>inferir e checar a compatibilidade de tipos de cada expressão,
 *       declaração, atribuição e chamada — com suporte a herança/polimorfismo
 *       (uma classe filha pode ser usada onde se espera a classe mãe).</li>
 * </ul>
 */
public class AnalisadorSemantico extends DepthFirstAdapter {

    private final TabelaClasses classes;
    private final GerenciadorErros erros;
    private final PilhaEscopos pilha = new PilhaEscopos();

    /** Tipo já inferido de cada nó de expressão visitado (preenchido nos métodos outXxxExp). */
    private final IdentityHashMap<PExp, Tipo> tiposExp = new IdentityHashMap<>();
    /** Tipo do valor produzido por cada bloco de expressão (start ... exp finish). */
    private final IdentityHashMap<dplusplus.node.PBlocoExp, Tipo> tiposBlocoExp = new IdentityHashMap<>();
    /** Tipo de retorno já resolvido de cada chamada (VOID para procedimentos). */
    private final IdentityHashMap<dplusplus.node.PChamada, Tipo> tiposChamada = new IdentityHashMap<>();

    private InfoClasse classeAtual;
    private Simbolo metodoAtual;
    private Tipo retornoEsperado;

    public AnalisadorSemantico(TabelaClasses classes, GerenciadorErros erros) {
        this.classes = classes;
        this.erros = erros;
    }

    private Tipo t(PExp exp) {
        return tiposExp.getOrDefault(exp, Tipo.ERRO);
    }

    // ---------------------------------------------------------------
    // Início/fim
    // ---------------------------------------------------------------

    @Override
    public void inStart(Start node) {
        System.out.println("-------------------------------------------------");
        System.out.println("Iniciando análise semântica...");
    }

    @Override
    public void outStart(Start node) {
        System.out.println("Análise semântica concluída.");
    }

    // ---------------------------------------------------------------
    // Classes: entra/sai do escopo de membros (achatado com herança)
    // ---------------------------------------------------------------

    @Override
    public void inADefClasseDefClasse(ADefClasseDefClasse node) {
        String nome = ConversorTipos.nomeDaClasse(node.getTipoClasse());
        classeAtual = classes.buscar(nome);
        if (classeAtual != null) {
            pilha.abrirEscopo(classeAtual.getTabelaMembros());
        } else {
            pilha.abrirEscopo();
        }
    }

    @Override
    public void outADefClasseDefClasse(ADefClasseDefClasse node) {
        pilha.fecharEscopo();
        classeAtual = null;
    }

    // ---------------------------------------------------------------
    // Declarações de objeto/variável/constante (atributo de classe OU
    // declaração local dentro de um bloco start...finish — o mesmo nó da
    // AST é reaproveitado nos dois contextos pela gramática).
    // ---------------------------------------------------------------

    @Override
    public void outAObjetoAtributosAlt(AObjetoAtributosAlt node) {
        boolean nivelDeClasse = node.parent() instanceof ADefClasseDefClasse;
        String nomeClasseAtributo = ConversorTipos.nomeDaClasse(node.getTipoClasse());

        if (!classes.existeClasse(nomeClasseAtributo)) {
            if (!nivelDeClasse) {
                erros.erro(ConversorTipos.tokenDaClasse(node.getTipoClasse()),
                    "Classe '" + nomeClasseAtributo + "' não foi declarada.");
                declararLocalObjeto(node.getId(), Tipo.ERRO);
            }
            return;
        }

        InfoClasse infoObjeto = classes.buscar(nomeClasseAtributo);
        if (infoObjeto.isAbstrata()) {
            erros.erro(node.getId(), "'" + nomeClasseAtributo
                + "' " + (infoObjeto.isRaiz() ? "é a classe Root e" : "é uma classe abstrata e")
                + " não pode ser instanciada (declaração de '" + node.getId().getText() + "').");
        }

        if (!nivelDeClasse) {
            declararLocalObjeto(node.getId(), Tipo.classe(nomeClasseAtributo));
        }
    }

    private void declararLocalObjeto(TId id, Tipo tipo) {
        Simbolo s = new Simbolo(id.getText(), Simbolo.Categoria.OBJETO, tipo, id.getLine(), id.getPos());
        s.setInicializado(true);
        declararLocal(s, id);
    }

    @Override
    public void outAVariavelAtributosAlt(AVariavelAtributosAlt node) {
        boolean nivelDeClasse = node.parent() instanceof ADefClasseDefClasse;
        Tipo tipoDeclarado = ConversorTipos.deTipoPrimitivo(node.getTipoPrimitivo());
        Tipo tipoValor = t(node.getValor());
        if (!Tipo.atribuivel(tipoDeclarado, tipoValor, classes)) {
            erros.erro(node.getId(), "Não é possível inicializar '" + node.getId().getText()
                + "' (declarada como " + tipoDeclarado + ") com um valor do tipo " + tipoValor + ".");
        }
        if (!nivelDeClasse) {
            Simbolo s = new Simbolo(node.getId().getText(), Simbolo.Categoria.VARIAVEL, tipoDeclarado,
                node.getId().getLine(), node.getId().getPos());
            s.setInicializado(true);
            declararLocal(s, node.getId());
        }
    }

    @Override
    public void outAConstanteAtributosAlt(AConstanteAtributosAlt node) {
        boolean nivelDeClasse = node.parent() instanceof ADefClasseDefClasse;
        Tipo tipoDeclarado = ConversorTipos.deTipoPrimitivo(node.getTipoPrimitivo());
        Tipo tipoValor = t(node.getValor());
        if (!Tipo.atribuivel(tipoDeclarado, tipoValor, classes)) {
            erros.erro(node.getId(), "Não é possível inicializar '" + node.getId().getText()
                + "' (declarada como " + tipoDeclarado + ") com um valor do tipo " + tipoValor + ".");
        }
        if (!nivelDeClasse) {
            Simbolo s = new Simbolo(node.getId().getText(), Simbolo.Categoria.CONSTANTE, tipoDeclarado,
                node.getId().getLine(), node.getId().getPos());
            s.setInicializado(true);
            declararLocal(s, node.getId());
        }
    }

    private void declararLocal(Simbolo s, Token tokenNome) {
        if (!pilha.inserir(s)) {
            erros.erro(tokenNome, "'" + s.getNome() + "' já foi declarado neste escopo.");
        }
    }

    // ---------------------------------------------------------------
    // Métodos: abre escopo de parâmetros (que engloba o corpo) e, para
    // funções, guarda o tipo de retorno esperado para conferir o corpo.
    // ---------------------------------------------------------------

    @Override
    public void inAProcedimentoMetodosAlt(AProcedimentoMetodosAlt node) {
        pilha.abrirEscopo();
        AProcedureHeaderProcedureHeader header = (AProcedureHeaderProcedureHeader) node.getHeader();
        metodoAtual = classeAtual != null
            ? classeAtual.getTabelaMembros().buscarLocal(header.getId().getText())
            : null;
    }

    @Override
    public void outAProcedimentoMetodosAlt(AProcedimentoMetodosAlt node) {
        metodoAtual = null;
        pilha.fecharEscopo();
    }

    @Override
    public void inAFuncaoMetodosAlt(AFuncaoMetodosAlt node) {
        pilha.abrirEscopo();
        AFunctionHeaderFunctionHeader header = (AFunctionHeaderFunctionHeader) node.getHeader();
        metodoAtual = classeAtual != null
            ? classeAtual.getTabelaMembros().buscarLocal(header.getId().getText())
            : null;
        retornoEsperado = metodoAtual != null ? metodoAtual.getTipo() : Tipo.ERRO;
    }

    @Override
    public void outAFuncaoMetodosAlt(AFuncaoMetodosAlt node) {
        if (node.getCorpo() != null) {
            Tipo tipoCorpo = tiposBlocoExp.getOrDefault(node.getCorpo(), Tipo.ERRO);
            if (!Tipo.atribuivel(retornoEsperado, tipoCorpo, classes)) {
                AFunctionHeaderFunctionHeader header = (AFunctionHeaderFunctionHeader) node.getHeader();
                erros.erro(header.getId(), "A função '" + header.getId().getText()
                    + "' deveria retornar " + retornoEsperado + ", mas o corpo produz " + tipoCorpo + ".");
            }
        }
        metodoAtual = null;
        retornoEsperado = null;
        pilha.fecharEscopo();
    }

    @Override
    public void outAParametroParametro(AParametroParametro node) {
        Node pai = node.parent();
        List<PParametro> lista = (pai instanceof AProcedureHeaderProcedureHeader)
            ? ((AProcedureHeaderProcedureHeader) pai).getParams()
            : ((AFunctionHeaderFunctionHeader) pai).getParams();
        int indice = lista.indexOf(node);
        Tipo tipo = Tipo.ERRO;
        if (metodoAtual != null && indice >= 0 && indice < metodoAtual.getTiposParametros().size()) {
            tipo = metodoAtual.getTiposParametros().get(indice);
        }
        Simbolo s = new Simbolo(node.getId().getText(), Simbolo.Categoria.PARAMETRO, tipo,
            node.getId().getLine(), node.getId().getPos());
        s.setInicializado(true);
        declararLocal(s, node.getId());
    }

    // ---------------------------------------------------------------
    // Blocos start...finish: cada um é um novo escopo (hash table nova).
    // ---------------------------------------------------------------

    @Override
    public void inABlocoCmdBlocoCmd(ABlocoCmdBlocoCmd node) {
        pilha.abrirEscopo();
    }

    @Override
    public void outABlocoCmdBlocoCmd(ABlocoCmdBlocoCmd node) {
        pilha.fecharEscopo();
    }

    @Override
    public void inABlocoExpBlocoExp(ABlocoExpBlocoExp node) {
        pilha.abrirEscopo();
    }

    @Override
    public void outABlocoExpBlocoExp(ABlocoExpBlocoExp node) {
        tiposBlocoExp.put(node, t(node.getValor()));
        pilha.fecharEscopo();
    }

    // ---------------------------------------------------------------
    // Comandos
    // ---------------------------------------------------------------

    @Override
    public void outAIfComando(AIfComando node) {
        Tipo tCond = t(node.getCond());
        if (!tCond.pareceAnswer()) {
            erroExp(node.getCond(), "A condição de 'in case that' deve ser do tipo answer (recebeu " + tCond + ").");
        }
    }

    @Override
    public void outAWhileComando(AWhileComando node) {
        Tipo tCond = t(node.getCond());
        if (!tCond.pareceAnswer()) {
            erroExp(node.getCond(), "A condição de 'as long as' deve ser do tipo answer (recebeu " + tCond + ").");
        }
    }

    @Override
    public void outAAtribuicaoComando(AAtribuicaoComando node) {
        String nome = node.getId().getText();
        Simbolo alvo = pilha.buscar(nome);
        Tipo tipoValor = t(node.getValor());

        if (alvo == null) {
            erros.erro(node.getId(), "'" + nome + "' não foi declarado.");
            return;
        }
        if (alvo.getCategoria() == Simbolo.Categoria.CLASSE || alvo.isMetodo()) {
            erros.erro(node.getId(), "'" + nome + "' não é uma variável, um objeto ou uma constante; não pode receber atribuição.");
            return;
        }
        if (alvo.getCategoria() == Simbolo.Categoria.CONSTANTE) {
            erros.erro(node.getId(), "'" + nome + "' é uma constante (unalterable) e não pode ser modificada.");
            return;
        }
        if (!Tipo.atribuivel(alvo.getTipo(), tipoValor, classes)) {
            erros.erro(node.getId(), "Não é possível atribuir um valor do tipo " + tipoValor
                + " a '" + nome + "' (tipo " + alvo.getTipo() + ").");
        }
        alvo.setInicializado(true);
    }

    // ---------------------------------------------------------------
    // Chamadas (procedimento ou função, direta ou através de obj.->...)
    // ---------------------------------------------------------------

    @Override
    public void outAChamadaChamada(AChamadaChamada node) {
        Simbolo metodo = resolverAcesso(node.getAcessos(), node.getNome(), true);
        if (metodo == null) {
            tiposChamada.put(node, Tipo.ERRO);
            return;
        }

        List<Tipo> tiposParam = metodo.getTiposParametros();
        List<PExp> args = node.getArgs();
        if (tiposParam.size() != args.size()) {
            erros.erro(node.getNome(), "'" + metodo.getNome() + "' espera " + tiposParam.size()
                + " argumento(s), mas foi chamado com " + args.size() + ".");
        } else {
            for (int i = 0; i < args.size(); i++) {
                Tipo tipoArg = t(args.get(i));
                Tipo tipoParam = tiposParam.get(i);
                if (!Tipo.atribuivel(tipoParam, tipoArg, classes)) {
                    erros.erro(node.getNome(), "O argumento " + (i + 1) + " de '" + metodo.getNome()
                        + "' deveria ser do tipo " + tipoParam + ", mas é " + tipoArg + ".");
                }
            }
        }

        tiposChamada.put(node, metodo.getCategoria() == Simbolo.Categoria.FUNCAO ? metodo.getTipo() : Tipo.VOID);
    }

    @Override
    public void outAChamadaExp(AChamadaExp node) {
        Tipo tipo = tiposChamada.getOrDefault(node.getChamada(), Tipo.ERRO);
        if (tipo.isVoid()) {
            AChamadaChamada chamada = (AChamadaChamada) node.getChamada();
            erros.erro(chamada.getNome(), "'" + chamada.getNome().getText()
                + "' é um procedimento (não retorna valor) e não pode ser usado como expressão.");
            tipo = Tipo.ERRO;
        }
        tiposExp.put(node, tipo);
    }

    @Override
    public void outAAtributoExp(AAtributoExp node) {
        Simbolo alvo = resolverAcesso(node.getAcessos(), node.getNome(), false);
        if (alvo == null) {
            tiposExp.put(node, Tipo.ERRO);
            return;
        }
        if (!alvo.isInicializado()) {
            erros.erro(node.getNome(), "'" + alvo.getNome() + "' está sendo usado antes de ser inicializado.");
        }
        tiposExp.put(node, alvo.getTipo() != null ? alvo.getTipo() : Tipo.ERRO);
    }

    /**
     * Resolve uma cadeia de acesso {@code acesso1.->acesso2.-> ... .->alvo} (ou apenas
     * {@code alvo}, se a cadeia estiver vazia — caso mais comum, de acesso direto).
     * O primeiro identificador (quando a cadeia não está vazia) e o identificador final
     * (quando a cadeia ESTÁ vazia) são buscados na pilha de escopos léxicos — o que
     * inclui, no fundo da pilha, os membros (próprios e herdados) da classe atual,
     * implementando "posso chamar diretamente o que pertence ao meu objeto ou às
     * minhas ancestrais". Os demais elos da cadeia são buscados como atributos da
     * classe do elo anterior (acesso a outro objeto via {@code ->}).
     */
    private Simbolo resolverAcesso(List<TId> acessos, TId alvoTok, boolean alvoEhMetodo) {
        InfoClasse contexto = null;

        for (TId acessoTok : acessos) {
            Simbolo s = (contexto == null)
                ? pilha.buscar(acessoTok.getText())
                : contexto.getTabelaMembros().buscarLocal(acessoTok.getText());

            if (s == null) {
                if (contexto == null) {
                    erros.erro(acessoTok, "'" + acessoTok.getText() + "' não foi declarado.");
                } else {
                    erros.erro(acessoTok, "'" + acessoTok.getText() + "' não é um atributo de '" + contexto.getNome() + "'.");
                }
                return null;
            }
            if (s.getCategoria() != Simbolo.Categoria.OBJETO) {
                erros.erro(acessoTok, "'" + acessoTok.getText() + "' não é um objeto; não é possível acessar membros através de '->'.");
                return null;
            }
            if (!s.isInicializado()) {
                erros.erro(acessoTok, "'" + acessoTok.getText() + "' está sendo usado antes de ser inicializado.");
            }
            String nomeClasse = (s.getTipo() != null && s.getTipo().isClasse()) ? s.getTipo().getNomeClasse() : null;
            contexto = (nomeClasse == null) ? null : classes.buscar(nomeClasse);
            if (contexto == null) {
                return null;
            }
        }

        Simbolo alvo = (contexto == null)
            ? pilha.buscar(alvoTok.getText())
            : contexto.getTabelaMembros().buscarLocal(alvoTok.getText());

        if (alvo == null) {
            if (contexto == null) {
                erros.erro(alvoTok, "'" + alvoTok.getText() + "' não foi declarado.");
            } else {
                erros.erro(alvoTok, "'" + alvoTok.getText() + "' não é um membro de '" + contexto.getNome() + "'.");
            }
            return null;
        }

        if (alvoEhMetodo) {
            if (!alvo.isMetodo()) {
                erros.erro(alvoTok, "'" + alvoTok.getText() + "' não é um procedimento ou função.");
                return null;
            }
        } else {
            if (alvo.isMetodo() || alvo.getCategoria() == Simbolo.Categoria.CLASSE) {
                erros.erro(alvoTok, "'" + alvoTok.getText() + "' não pode ser usado como valor nesta expressão.");
                return null;
            }
        }
        return alvo;
    }

    // ---------------------------------------------------------------
    // Expressões: cada método out* infere e valida o tipo do nó,
    // guardando o resultado em tiposExp para uso pelo nó pai.
    // ---------------------------------------------------------------

    @Override public void outARealExp(ARealExp node) { tiposExp.put(node, Tipo.NUMBER); }
    @Override public void outAInteiroExp(AInteiroExp node) { tiposExp.put(node, Tipo.NUMBER); }
    @Override public void outAYesExp(AYesExp node) { tiposExp.put(node, Tipo.ANSWER); }
    @Override public void outANoExp(ANoExp node) { tiposExp.put(node, Tipo.ANSWER); }

    @Override public void outABlocoExp(ABlocoExp node) {
        tiposExp.put(node, tiposBlocoExp.getOrDefault(node.getBlocoExp(), Tipo.ERRO));
    }

    @Override public void outAPlusExp(APlusExp node) { checarAritmetico(node, node.getEsq(), node.getDir(), "+"); }
    @Override public void outAMinusExp(AMinusExp node) { checarAritmetico(node, node.getEsq(), node.getDir(), "-"); }
    @Override public void outAMultExp(AMultExp node) { checarAritmetico(node, node.getEsq(), node.getDir(), "*"); }
    @Override public void outADivExp(ADivExp node) { checarAritmetico(node, node.getEsq(), node.getDir(), "/"); }

    private void checarAritmetico(PExp node, PExp esq, PExp dir, String operador) {
        Tipo tEsq = t(esq);
        Tipo tDir = t(dir);
        if (!tEsq.pareceNumber() || !tDir.pareceNumber()) {
            erroExp(node, "Operador '" + operador + "' exige operandos do tipo number (recebeu " + tEsq + " e " + tDir + ").");
            tiposExp.put(node, Tipo.ERRO);
        } else {
            tiposExp.put(node, Tipo.NUMBER);
        }
    }

    @Override public void outALtExp(ALtExp node) { checarRelacional(node, node.getEsq(), node.getDir(), "<"); }
    @Override public void outAGtExp(AGtExp node) { checarRelacional(node, node.getEsq(), node.getDir(), ">"); }

    private void checarRelacional(PExp node, PExp esq, PExp dir, String operador) {
        Tipo tEsq = t(esq);
        Tipo tDir = t(dir);
        if (!tEsq.pareceNumber() || !tDir.pareceNumber()) {
            erroExp(node, "Operador '" + operador + "' exige operandos do tipo number (recebeu " + tEsq + " e " + tDir + ").");
            tiposExp.put(node, Tipo.ERRO);
        } else {
            tiposExp.put(node, Tipo.ANSWER);
        }
    }

    @Override public void outAAndExp(AAndExp node) { checarLogico(node, node.getEsq(), node.getDir(), "and"); }
    @Override public void outAOrExp(AOrExp node) { checarLogico(node, node.getEsq(), node.getDir(), "or"); }

    private void checarLogico(PExp node, PExp esq, PExp dir, String operador) {
        Tipo tEsq = t(esq);
        Tipo tDir = t(dir);
        if (!tEsq.pareceAnswer() || !tDir.pareceAnswer()) {
            erroExp(node, "Operador '" + operador + "' exige operandos do tipo answer (recebeu " + tEsq + " e " + tDir + ").");
            tiposExp.put(node, Tipo.ERRO);
        } else {
            tiposExp.put(node, Tipo.ANSWER);
        }
    }

    @Override
    public void outAEqExp(AEqExp node) {
        Tipo tEsq = t(node.getEsq());
        Tipo tDir = t(node.getDir());
        boolean compativel = tEsq.isErro() || tDir.isErro()
            || Tipo.atribuivel(tEsq, tDir, classes) || Tipo.atribuivel(tDir, tEsq, classes);
        if (!compativel) {
            erroExp(node, "Operador '=' exige operandos de tipos compatíveis (recebeu " + tEsq + " e " + tDir + ").");
            tiposExp.put(node, Tipo.ERRO);
        } else {
            tiposExp.put(node, Tipo.ANSWER);
        }
    }

    @Override
    public void outANegExp(ANegExp node) {
        Tipo tOperando = t(node.getOperando());
        if (!tOperando.pareceNumber()) {
            erroExp(node, "O operador unário '-' exige um operando do tipo number (recebeu " + tOperando + ").");
            tiposExp.put(node, Tipo.ERRO);
        } else {
            tiposExp.put(node, Tipo.NUMBER);
        }
    }

    @Override
    public void outANotExp(ANotExp node) {
        Tipo tOperando = t(node.getOperando());
        if (!tOperando.pareceAnswer()) {
            erroExp(node, "O operador unário '!' exige um operando do tipo answer (recebeu " + tOperando + ").");
            tiposExp.put(node, Tipo.ERRO);
        } else {
            tiposExp.put(node, Tipo.ANSWER);
        }
    }

    @Override
    public void outATernarioExp(ATernarioExp node) {
        Tipo tCond = t(node.getCond());
        if (!tCond.pareceAnswer()) {
            erroExp(node, "A condição do operador ternário 'if...else' deve ser do tipo answer (recebeu " + tCond + ").");
        }
        Tipo tVerdadeiro = t(node.getVerdadeiro());
        Tipo tFalso = t(node.getFalso());
        Tipo unificado = Tipo.unificar(tVerdadeiro, tFalso, classes);
        if (unificado.isErro() && !tVerdadeiro.isErro() && !tFalso.isErro()) {
            erroExp(node, "Os dois ramos do operador ternário têm tipos incompatíveis (" + tVerdadeiro + " e " + tFalso + ").");
        }
        tiposExp.put(node, unificado);
    }

    // ---------------------------------------------------------------
    // Utilidades de mensagem de erro
    // ---------------------------------------------------------------

    private void erroExp(Node origem, String mensagem) {
        Token tok = primeiroToken(origem);
        if (tok != null) {
            erros.erro(tok, mensagem);
        } else {
            erros.erro(0, 0, mensagem);
        }
    }

    /** Encontra um token representativo (o mais à esquerda) de uma subárvore de expressão, só para posicionar mensagens de erro. */
    private Token primeiroToken(Node n) {
        if (n instanceof AAtributoExp) {
            AAtributoExp a = (AAtributoExp) n;
            return a.getAcessos().isEmpty() ? a.getNome() : a.getAcessos().get(0);
        }
        if (n instanceof AChamadaExp) return primeiroToken(((AChamadaExp) n).getChamada());
        if (n instanceof AChamadaChamada) {
            AChamadaChamada c = (AChamadaChamada) n;
            return c.getAcessos().isEmpty() ? c.getNome() : c.getAcessos().get(0);
        }
        if (n instanceof ARealExp) return ((ARealExp) n).getValorReal();
        if (n instanceof AInteiroExp) return ((AInteiroExp) n).getValorInteiro();
        if (n instanceof ATernarioExp) return primeiroToken(((ATernarioExp) n).getCond());
        if (n instanceof AOrExp) return primeiroToken(((AOrExp) n).getEsq());
        if (n instanceof AAndExp) return primeiroToken(((AAndExp) n).getEsq());
        if (n instanceof AEqExp) return primeiroToken(((AEqExp) n).getEsq());
        if (n instanceof ALtExp) return primeiroToken(((ALtExp) n).getEsq());
        if (n instanceof AGtExp) return primeiroToken(((AGtExp) n).getEsq());
        if (n instanceof APlusExp) return primeiroToken(((APlusExp) n).getEsq());
        if (n instanceof AMinusExp) return primeiroToken(((AMinusExp) n).getEsq());
        if (n instanceof AMultExp) return primeiroToken(((AMultExp) n).getEsq());
        if (n instanceof ADivExp) return primeiroToken(((ADivExp) n).getEsq());
        if (n instanceof ANegExp) return primeiroToken(((ANegExp) n).getOperando());
        if (n instanceof ANotExp) return primeiroToken(((ANotExp) n).getOperando());
        if (n instanceof ABlocoExp) return primeiroToken(((ABlocoExp) n).getBlocoExp());
        if (n instanceof ABlocoExpBlocoExp) return primeiroToken(((ABlocoExpBlocoExp) n).getValor());
        if (n instanceof AIfComando) return primeiroToken(((AIfComando) n).getCond());
        if (n instanceof AWhileComando) return primeiroToken(((AWhileComando) n).getCond());
        return null;
    }
}
