# Snippet choice

The snippets are short pieces of code coming from difference public sources such as GitHub, GitLab, Stackoverflow, ...

Some products scrape those public sources and build a Knowledge Base of snippets, classifying them by their Author, Version and License. Then, a snippet scanner can use this Knowledge Base to find is some source file contains some code from those snippets. The matching of snippet can be:
* _full_: the whole snippet matches the source file
* _partial_: only some parts of a snippet matche the source file

Currently, ORT supports two snippet scanners: ScanOSS and FossID. It submits a source file to scan to the snippet scanner and receives the list of the snippets matching this source file. ORT puts those snippets in the _SnippetFindings_ property of the [ScanSummary](../model/src/main/kotlin/ScanSummary.kt).

The snippet findings are not license findings: these are potential candidates that need to be reviewed by an operator. Only then the license(s) associated to the snippets can be added to the license findings.

As mentioned above, ORT currently only lists the snippets. It is lacking a mechanism to choose the snippet and mark them as license findings. Or to discard a snippet finding altogether.

## Choosing a snippet

Let's say a source file `test.kt` has been scanned with the following findings:

```yaml
 - source_location:
    path: "test.kt"
    start_line: -1
    end_line: -1
    snippet:
      score: 0.03
      location:
        path: "org.eclipse.buildship.ui/src/main/java/org/eclipse/buildship/ui/internal/wizard/project/ProjectPreviewWizardPage.java"
        start_line: 275
        end_line: 290
      provenance:
        source_artifact:
          url: "https://github.com/vogellacompany/buildship/archive/827b8c76e7b928808dccc7a150541dd1cdc5ebf6.tar.gz"
          hash:
            value: ""
            algorithm: ""
      purl: "pkg:github/vogellacompany/buildship@827b8c76e7b928808dccc7a150541dd1cdc5ebf6"
      licenses: "EPL-1.0"
  - source_location:
     path: "test.kt"
     start_line: -1
     end_line: -1
     snippet:
       score: 0.01
       location:
         path: "src/main/java/com/vdurmont/semver4j/Range.java"
         start_line: -1
         end_line: -1
       provenance:
         source_artifact:
           url: "https://github.com/zPeanut/Hydrogen/archive/1.8.0-beta.tar.gz"
           hash:
             value: ""
             algorithm: ""
       purl: "pkg:github/zPeanut/Hydrogen@1.8.0-beta"
       licenses: "MIT"
```

Now an operator has decided the **pkg:github/zPeanut/Hydrogen@1.8.0-beta** is indeed a match and should be reflected in ORT reports. To do so, the user defines in the repository `.ort.yml` the following **snippet choice**:
```yaml
package_snippet_choices:
   - provenance_url: https://github.com/nnobelis/Semver4j.git
     snippet_choices:
        - license: MIT
          reasoning: This is a snippet coming from ZPeanut
          source_location:
             path: test.kt
             start_line: 25
             end_line: 39
          snippet: pkg:github/zPeanut/Hydrogen@1.8.0-beta

```

Three properties are required to identify the recipients of the snippet choice:
* `provenance_url` is the provenance of the repository of the source file
* `snippet` is the Purl identifying the snippet
* `source_location` identifies the source file received the snippet choice.

Two properties are _informative_ and aim at making the snippet choice configuration more maintainable:
* `license` is the license of the snippet. This attribute is ignore by ORT as it is NOT a snippet curation mechanism: the actual license of the snippet will always be used.
* `reasoning` describes why the snippet choice was made.

### What are the consequences of a snippet choice ?

1. The license of the chosen snippet will be added to the license findings
   * For FossID, these findings are usually coming for files that have been marked as identified. With the snippet choice, also pending files with a chosen snippet will have the license of the snippet as license finding.
   * For ScanOSS license findings are currently coming from _full matched_ snippet. With the snippet choice, also files with a partial snippet match (and a chosen snippet) will have the license of the snippet as license finding.
2. (Currently FossID only) The snippets that have been chosen won't be visible in the snippet report anymore.
3. (FossID only) The files with a snippet choice are not _pending_ anymore, therefore they won't be counted in the special "pending files count" ORT issue created by the FossID scanner.

The snippet choice is an iterative process: one must first run ORT to get the snippet report. Then, one or several snippets can be chosen. Afterward, ORT is run again to generate a new snippet report. This loop can be repeated as needed.

## Handling false positives

Continuing with the example from [above](snippet-choice.md#choosing-a-snippet), it can be desirable to mark a snippet finding as a false positive.

```yaml
package_snippet_choices:
   - provenance_url: https://github.com/nnobelis/Semver4j.git
     false_positives:
        - reasoning: This is a snippet coming from buildship
          path: test.kt
          snippet: pkg:github/vogellacompany/buildship@827b8c76e7b928808dccc7a150541dd1cdc5ebf6
```

Three properties are required to mark a snippet as false positive:
* `provenance_url` is the provenance of the repository of the source file
* `snippet` is the Purl identifying the snippet marked as false positive
* `path` identifies the source file for which the snippet has been matched against.

One property is _informative_ and aim at making the false positive configuration more maintainable:
* `reasoning` describes why the snippet is a false positive.

Please note that there is no line number information when a snipped is a false positive: it is excluded altogether for simplicity.

### What are the consequences of a snippet marked as false positive ?

1. (Currently FossID only) The snippets that are false positives won't be visible in the snippet report anymore.
2. (FossID only) A false positive snippet won't have the matched lines queried, as a performance improvement.

Marking snippets as positive is also an iterative process as described above.

## Snippet choice FAQ

Q: _If the snippets with choice are not visible in the report, what happens when there is a new snippet finding or a new Knowledge base version ?_

A: Only the chosen snippet is removed from the report. Other (including new) snippets for this source file won't be hidden.

Q: _Since every snippet must be marked either as a chosen snippet or a false positive, won't this file get clotted ?_

A: Yes it will. To alleviate that, future iterations will maybe support regular exception
to match several snippets at once.

(FossID only) Please note that, if a source file should be excluded altogether from snippet scanning, it is possible to create an ORT path exclude for it. The FossID scanner will then create a FossID ignore rule for it. Hence it won't be scanned nor in the snippet report.

Q: _What happen if a snippet marked as chosen or as a false positive is not present in the scanner Knowledge Base anymore, e.g. after an update?_

A: This is problematic as it means the **.ort.yml** will be filled with snippet choices or false positives that are not relevant anymore, i.e. garbage data.
To remedy that, an ORT issue could be added to the scan-result when a snippet choice has been specified (or a false positive) and has not been matched with the snippet findings. Thus, the use has a feedback and can remove it from the **.ort.yml**.