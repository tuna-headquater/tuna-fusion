package ai.tuna.fusion.gitops.test;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author robinqu
 */
@Slf4j
public class RepoTest {


    @Test
    public void testTreeWalker() throws IOException {
        // 替换为你的 Git 仓库路径
        String repoPath = "/Users/robinqu/Workspace/github/eclipse-jgit/jgit";

        try (Repository repository = Git.open(new File(repoPath)).getRepository()) {
            // 获取 HEAD 提交的 ID
            ObjectId head = repository.resolve("HEAD");

            // 使用 RevWalk 解析提交
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(head);

                // 使用 TreeWalk 遍历仓库文件树
                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true); // 启用递归遍历

                    System.out.println("Git 仓库文件列表:");
                    while (treeWalk.next()) {
                        String path = treeWalk.getPathString();
                        System.out.println(path);
                    }
                }
            }
        }
    }


}
