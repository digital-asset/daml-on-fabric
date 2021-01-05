#docker build . -t digitalasset/daml-on-fabric-chaincode:2.2.0

FROM golang:1.13.5-alpine AS build_base

RUN apk add --update bash gcc g++ git

WORKDIR /go/src/chaincode

ENV GO111MODULE=on

COPY go.mod .
COPY go.sum .

RUN go mod download

FROM build_base AS server_builder

COPY . .

RUN go mod vendor

RUN CGO_ENABLED=1 GOOS=linux GOARCH=amd64 go install -a -tags netgo -ldflags '-w -extldflags "-static"' .

RUN echo "Everything seems fine ..."

FROM alpine AS weaviate

RUN apk add bash

COPY --from=server_builder /go/bin /bin/chaincode

CMD /bin/chaincode/daml_on_fabric
