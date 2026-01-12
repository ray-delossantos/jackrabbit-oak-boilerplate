package com.aem.oak.core.repository;

import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import java.util.function.Function;

/**
 * Factory for creating and managing JCR sessions.
 * Provides session pooling and automatic resource management.
 */
@Component(service = JcrSessionFactory.class, immediate = true)
public class JcrSessionFactory {

    private static final Logger LOG = LoggerFactory.getLogger(JcrSessionFactory.class);

    private static final String DEFAULT_WORKSPACE = null; // Default workspace
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "admin";

    @Reference
    private OakRepositoryInitializer repositoryInitializer;

    private SimpleCredentials adminCredentials;
    private SimpleCredentials serviceCredentials;

    @Activate
    protected void activate() {
        // Initialize credentials from environment or defaults
        String adminUser = getEnvOrDefault("JCR_ADMIN_USER", ADMIN_USER);
        String adminPass = getEnvOrDefault("JCR_ADMIN_PASSWORD", ADMIN_PASSWORD);

        adminCredentials = new SimpleCredentials(adminUser, adminPass.toCharArray());
        serviceCredentials = new SimpleCredentials("service", "service".toCharArray());

        LOG.info("JCR Session Factory activated");
    }

    /**
     * Creates a new admin session.
     * Caller is responsible for calling session.logout().
     *
     * @return a new Session with admin privileges
     * @throws RepositoryException if session creation fails
     */
    public Session createAdminSession() throws RepositoryException {
        return createSession(adminCredentials);
    }

    /**
     * Creates a new service session for background operations.
     * Caller is responsible for calling session.logout().
     *
     * @return a new Session with service privileges
     * @throws RepositoryException if session creation fails
     */
    public Session createServiceSession() throws RepositoryException {
        return createSession(serviceCredentials);
    }

    /**
     * Creates a session with the specified credentials.
     *
     * @param credentials the credentials to use
     * @return a new Session
     * @throws RepositoryException if session creation fails
     */
    public Session createSession(Credentials credentials) throws RepositoryException {
        Repository repository = repositoryInitializer.getRepository();
        if (repository == null) {
            throw new RepositoryException("Repository not initialized");
        }
        return repository.login(credentials, DEFAULT_WORKSPACE);
    }

    /**
     * Creates a session for a specific user.
     *
     * @param userId   the user ID
     * @param password the user's password
     * @return a new Session
     * @throws RepositoryException if session creation fails
     */
    public Session createUserSession(String userId, String password) throws RepositoryException {
        SimpleCredentials credentials = new SimpleCredentials(userId, password.toCharArray());
        return createSession(credentials);
    }

    /**
     * Executes an operation within a session, handling session lifecycle.
     * The session is automatically closed after the operation completes.
     *
     * @param operation the operation to execute
     * @param <T>       the return type
     * @return the result of the operation
     * @throws RepositoryException if the operation fails
     */
    public <T> T doWithSession(SessionOperation<T> operation) throws RepositoryException {
        Session session = createAdminSession();
        try {
            return operation.execute(session);
        } finally {
            if (session != null && session.isLive()) {
                session.logout();
            }
        }
    }

    /**
     * Executes an operation that modifies the repository.
     * The session is automatically saved and closed after the operation.
     *
     * @param operation the operation to execute
     * @param <T>       the return type
     * @return the result of the operation
     * @throws RepositoryException if the operation fails
     */
    public <T> T doWithSessionAndSave(SessionOperation<T> operation) throws RepositoryException {
        Session session = createAdminSession();
        try {
            T result = operation.execute(session);
            if (session.hasPendingChanges()) {
                session.save();
            }
            return result;
        } finally {
            if (session != null && session.isLive()) {
                session.logout();
            }
        }
    }

    /**
     * Executes a void operation within a session.
     *
     * @param operation the operation to execute
     * @throws RepositoryException if the operation fails
     */
    public void doWithSessionVoid(VoidSessionOperation operation) throws RepositoryException {
        Session session = createAdminSession();
        try {
            operation.execute(session);
        } finally {
            if (session != null && session.isLive()) {
                session.logout();
            }
        }
    }

    /**
     * Executes a void operation with automatic save.
     *
     * @param operation the operation to execute
     * @throws RepositoryException if the operation fails
     */
    public void doWithSessionVoidAndSave(VoidSessionOperation operation) throws RepositoryException {
        Session session = createAdminSession();
        try {
            operation.execute(session);
            if (session.hasPendingChanges()) {
                session.save();
            }
        } finally {
            if (session != null && session.isLive()) {
                session.logout();
            }
        }
    }

    /**
     * Gets the underlying repository.
     *
     * @return the Repository instance
     */
    public Repository getRepository() {
        return repositoryInitializer.getRepository();
    }

    /**
     * Checks if the repository is ready.
     *
     * @return true if repository is available
     */
    public boolean isReady() {
        return repositoryInitializer.isReady();
    }

    /**
     * Functional interface for session operations returning a value.
     */
    @FunctionalInterface
    public interface SessionOperation<T> {
        T execute(Session session) throws RepositoryException;
    }

    /**
     * Functional interface for void session operations.
     */
    @FunctionalInterface
    public interface VoidSessionOperation {
        void execute(Session session) throws RepositoryException;
    }

    private String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}
