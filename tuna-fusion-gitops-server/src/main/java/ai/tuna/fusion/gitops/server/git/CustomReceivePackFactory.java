package ai.tuna.fusion.gitops.server.git;

import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/**
 * @author robinqu
 */
public class CustomReceivePackFactory implements ReceivePackFactory<HttpServletRequest> {

    private final PreReceiveHook preReceiveHook;

    public CustomReceivePackFactory(PreReceiveHook preReceiveHook) {
        this.preReceiveHook = preReceiveHook;
    }

    @Override
    public ReceivePack create(HttpServletRequest req, Repository db) throws ServiceNotEnabledException, ServiceNotAuthorizedException {
        ReceivePack receivePack = new ReceivePack(db);
        receivePack.setPreReceiveHook(preReceiveHook);
        return receivePack;
    }

}
