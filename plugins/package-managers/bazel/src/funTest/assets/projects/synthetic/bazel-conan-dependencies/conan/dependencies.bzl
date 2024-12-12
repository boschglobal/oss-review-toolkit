# This Bazel module should be loaded by your WORKSPACE file.
# Add these lines to your WORKSPACE one (assuming that you're using the "bazel_layout"):
# load("@//conan:dependencies.bzl", "load_conan_dependencies")
# load_conan_dependencies()

def load_conan_dependencies():
    native.new_local_repository(
        name="fmt",
        path="/home/non1wa3/.conan2/p/fmt1eb60e0d8556e/p",
        build_file="/home/non1wa3/IdeaProjects/OSMI/oss-review-toolkit-internal/plugins/package-managers/bazel/src/funTest/assets/projects/synthetic/bazel-conan-dependencies/conan/fmt/BUILD.bazel",
    )
