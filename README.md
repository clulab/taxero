# taxero

To launch taxero you first need to build an odinson index.
See https://github.com/lum-ai/odinson for details.

Then you need to clone taxero:

    git clone git@github.com:clulab/taxero.git
    cd taxero
    
Open the file `taxero/src/main/resources/application.conf`
and set the variable `odinson.indexDir` to the index that contains the odinson index,
and set `taxero.wordEmbeddings` to the path of the embeddings file.

To launch taxero type

    sbt webapp/run
    
and then open http://localhost:9000 on your browser for the main taxero UI,
or http://localhost:9000/dev for the UI used to try new rules.
