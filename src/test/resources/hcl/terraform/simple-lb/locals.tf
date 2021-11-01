locals {
  default_subnet_id = tolist(data.aws_subnet_ids.this.ids)[0]
}