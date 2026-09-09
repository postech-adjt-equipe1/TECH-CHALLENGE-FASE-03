package br.com.fiap.historico.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Traduz excecoes dos resolvers para erros GraphQL com classificacao util
 * ao cliente (FORBIDDEN / BAD_REQUEST) em vez do INTERNAL_ERROR generico.
 */
@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(@NonNull Throwable ex, @NonNull DataFetchingEnvironment env) {
        if (ex instanceof AccessDeniedException) {
            return build(ex, env, ErrorType.FORBIDDEN);
        }
        if (ex instanceof IllegalArgumentException) {
            return build(ex, env, ErrorType.BAD_REQUEST);
        }
        return null;
    }

    private GraphQLError build(Throwable ex, DataFetchingEnvironment env, ErrorType type) {
        return GraphqlErrorBuilder.newError(env)
                .errorType(type)
                .message(ex.getMessage())
                .build();
    }
}
