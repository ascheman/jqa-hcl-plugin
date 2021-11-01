data "aws_vpc" "this" {}

data "aws_subnet_ids" "this" {
  vpc_id = data.aws_vpc.this.id
}