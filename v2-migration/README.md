# AWS SDK for Java v2 Migration Tool

## Description

* == [OpenRewrite][open-rewrite] recipes -- to automate -- migration from the AWS SDK Java v1 -- to -- AWS SDK Java v2

## Usage

* check [Developer Guide][developer-guide]

## Development

* `mvn clean install -pl :bom-internal,:bom,:v2-migration -P quick --am`
  * build this module


### Testing

* types of tests
  * Unit tests
    * | "test/"
    * use [RewriteTest][rewrite-test] interface
  * End-to-end functional tests
    * | [v2-migration-tests module][v2-migration-tests]
      * == sample applications / use AWS SDK for Java v1 vs transformed sample applications / use v2

[open-rewrite]: https://docs.openrewrite.org/
[rewrite-test]: https://docs.openrewrite.org/authoring-recipes/recipe-testing#rewritetest-interface
[v2-migration-tests]: ../test/v2-migration-tests
[developer-guide]: https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/migration-tool.html