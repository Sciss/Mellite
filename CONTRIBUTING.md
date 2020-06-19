# Contributing

We'd love for you to contribute to our source code and to improve this project!

 - [Issues and Bugs](#issue)
 - [Submission Guidelines](#submit)
 - [Coding Rules](#rules)
 - [Commit Message Guidelines](#commit)
 - [Signing the CLA](#cla)
 - [Further Info](#info)

## <a name="issue"></a> Found an Issue?

If you find a bug in the source code or a mistake in the documentation, you can help us by
submitting an issue to the project's git Repository. Even better you can submit a Pull Request
with a fix.

__Please see the Submission Guidelines below__.

## <a name="submit"></a> Submission Guidelines

### Submitting an Issue

Before you submit your issue search the archive, maybe your question was already answered.

If your issue appears to be a bug, and hasn't been reported, open a new issue.
Help us to maximise the effort we can spend fixing issues and adding new
features, by not reporting duplicate issues. Providing the following information will increase the
chances of your issue being dealt with quickly:

- __Overview of the Issue__ - if an error is being thrown a non-minified stack trace helps
- __Motivation for or Use Case__ - explain why this is a bug for you
- __Project Version(s)__ - is it a regression?
- __Environment and Operating System__ - state under which system the problem occurs (OS, Java and Scala version etc.)
- __Reproduce the Error__ - provide a simple reproducible example or an unambiguous set of steps.
- __Related Issues__ - has a similar issue been reported before?
- __Suggest a Fix__ - if you can't fix the bug yourself, perhaps you can point to what might be
  causing the problem (line of code or commit)

__If you get help, help others. That way everyone benefits!__

### Submitting a Pull Request

Before you submit your pull request consider the following guidelines:

- Search the git repository for an open or closed Pull Request
  that relates to your submission. You don't want to duplicate effort.
- Please sign our [Contributor License Agreement (CLA)](#cla) before sending pull
  requests. We cannot accept code without this.
- Make your changes in a new git branch:

     ```shell
     git checkout -b issue-descriptive-name-branch work
     ```

- Create your patch, __including appropriate test cases__ if applicable.
- Follow our [Coding Rules](#rules).
- Run the project's full test suite (if provided) and ensure that all tests pass.
- Commit your changes using a descriptive commit message that follows our
  [commit message conventions](#commit-message-format).

     ```shell
     git commit -a
     ```
  Note: the optional commit `-a` command line option will automatically "add" and "rm" edited files.

- Build your changes locally to ensure all the tests pass (typically `sbt clean test`).

- Push your branch to the git hosting service (e.g. GitLab):

    ```shell
    git push origin issue-descriptive-name-branch
    ```

- In the git hosting service (e.g. GitLab), send a pull request to the upstream `work` branch, 
  or to `main` branch if no `work` branch exists.
  If you are unsure about which branch to use as reference,
  consult with us first (through the issue tracker) to determine the best point of merge.
- If we suggest changes then:
    - Please make the required updates.
    - Re-run the test suite to ensure tests are still passing.
    - Commit your changes to your branch (e.g. `issue-descriptive-name-branch`).
    - Push the changes to your git repository on the hosting service (this will update your Pull Request).

If the PR gets too outdated we may ask you to rebase and force push to update the PR:

    ```shell
    git rebase work -i
    git push origin issue-descriptive-name-branch -f
    ```

_WARNING. Squashing or reverting commits and forced push thereafter may remove comments
on code on the git hosting service that were previously made by you and others in your commits._

That's it! Thank you for your contribution!

### After your pull request is merged

After your pull request is merged, you can safely delete your branch and pull the changes
from the main (upstream) repository:

- Delete the remote branch on the git hosting service (e.g. GitLab) either through its web UI or your local shell as follows:

    ```shell
    git push origin --delete issue-descriptive-name-branch
    ```

- Check out the `work` branch, or if it does not exist, the `main` branch (or the branch into which your work was merged):

    ```shell
    git checkout work -f
    ```

- Delete the local branch:

    ```shell
    git branch -D issue-descriptive-name-branch
    ```

- Update your `work` branch (or the branch into which your work was merged) with the latest upstream version:

    ```shell
    git pull --ff upstream work
    ```

## <a name="rules"></a> Coding Rules

To ensure consistency throughout the source code, keep these rules in mind as you are working:

- All features or bug fixes __should be tested__ by one or more unit tests. For Scala projects, 
  the preferred framework is [ScalaTest](http://scalatest.org/).
- All public API methods __should be documented__ (scaladoc for Scala projects, javadoc for Java projects).
- In general, follow the usual [Scala style guide](http://docs.scala-lang.org/style/). In particular
    - UTF-8 character encoding
    - two spaces indentation, no tabs
    - line breaks after max 120 characters
    - if you use IntelliJ IDEA, basically you can use the default auto-format of its Scala plugin.

## <a name="commit"></a> Git Commit Guidelines

### Commit Message Format

Use standard practice for the commit message. Use a subject
line no longer than 100 characters, followed by a body
outlining the details of the commit. If an issue in the issue
tracker exists, refer to it (e.g. `fixes #1234`). You can
also link to other commits by using the commit hash.

### Revert

If the commit reverts a previous commit, it should begin with `revert: `, followed by the header of the 
reverted commit. In the body it should say: `This reverts commit <hash>.`, where the hash is the SHA of the 
commit being reverted.

## <a name="cla"></a> Signing the CLA

Please sign our Contributor License Agreement (CLA) before sending pull requests. For any code
changes to be accepted, the CLA must be signed. It's a quick process, we promise!

- The process is provided through the separate project
  [github.com/Sciss/Contributing](https://github.com/Sciss/Contributing).
- In particular read the document [sign-cla.md](https://github.com/Sciss/Contributing/blob/main/sign-cla.md).

This CLA covers all projects at [git.iem.at/sciss](https://git.iem.at/sciss) 
or [github.com/Sciss](https://github.com/Sciss), so you should have to go through
this process only once. Thanks for your understanding.

## <a name="info"></a> Further Information

You can find out more detailed information about contributing in the project
[Sciss/Contributing](https://github.com/Sciss/Contributing).
