package dplusplus.semantico;

import dplusplus.node.AAnswerTipoPrimitivo;
import dplusplus.node.AClasseTipo;
import dplusplus.node.ANumberTipoPrimitivo;
import dplusplus.node.APrimitivoTipo;
import dplusplus.node.ATipoClasseTipoClasse;
import dplusplus.node.PTipo;
import dplusplus.node.PTipoClasse;
import dplusplus.node.PTipoPrimitivo;
import dplusplus.node.TIdClasse;

/**
 * Funções utilitárias (sem estado) que convertem os nós de tipo da árvore
 * sintática abstrata (gerados pelo SableCC a partir da gramática) para a
 * representação semântica {@link Tipo}. Usadas tanto pelo {@link ColetorDeClasses}
 * (passo 1: assinaturas de atributos/métodos) quanto pelo {@link AnalisadorSemantico}
 * (passo 2: declarações locais dentro de métodos).
 */
public final class ConversorTipos {

    private ConversorTipos() { }

    public static Tipo deTipoPrimitivo(PTipoPrimitivo no) {
        if (no instanceof AAnswerTipoPrimitivo) {
            return Tipo.ANSWER;
        }
        if (no instanceof ANumberTipoPrimitivo) {
            return Tipo.NUMBER;
        }
        return Tipo.ERRO;
    }

    public static String nomeDaClasse(PTipoClasse no) {
        return ((ATipoClasseTipoClasse) no).getIdClasse().getText();
    }

    public static TIdClasse tokenDaClasse(PTipoClasse no) {
        return ((ATipoClasseTipoClasse) no).getIdClasse();
    }

    /** Resolve um {@code tipo} (classe ou primitivo) checando, para tipos de classe, se a classe existe. */
    public static Tipo deTipo(PTipo no, TabelaClasses classes, GerenciadorErros erros) {
        if (no instanceof APrimitivoTipo) {
            return deTipoPrimitivo(((APrimitivoTipo) no).getTipoPrimitivo());
        }
        if (no instanceof AClasseTipo) {
            PTipoClasse tipoClasse = ((AClasseTipo) no).getTipoClasse();
            String nome = nomeDaClasse(tipoClasse);
            if (!classes.existeClasse(nome)) {
                erros.erro(tokenDaClasse(tipoClasse), "Classe '" + nome + "' não foi declarada.");
                return Tipo.ERRO;
            }
            return Tipo.classe(nome);
        }
        return Tipo.ERRO;
    }
}
