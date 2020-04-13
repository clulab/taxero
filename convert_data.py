import os
import sys
import glob
import json
import uuid


PARSED_DOCUMENTS_PATH = '/data/nlp/corpora/linnaeus/umbc/docs_new2'

def read_sentence(doc_id, sent_id):
    sent_id = int(sent_id)
    filename = os.path.join(PARSED_DOCUMENTS_PATH, f'{doc_id}.json')
    with open(filename) as f:
        doc = json.load(f)
        sentence = doc['sentences'][sent_id]
        sentence['docid'] = doc_id
        return sentence 

def read_sentences_from_tsv(filename):
    with open(filename) as f:
        for line in f:
            (doc_id, sent_id) =  line.strip().split('\t')
            yield read_sentence(doc_id, sent_id)

def get_field(sentence, name):
    for f in sentence['fields']:
        if f['name'] == name:
            return f

def heads_and_deprels(sentence):
    field = get_field(sentence, 'dependencies')
    heads = [None] * sentence['numTokens']
    deprels = [None] * sentence['numTokens']
    roots = field['roots']
    for root in roots:
       root = int(root)
       heads[root] = 0
       deprels[root] = "ROOT" 
    for (head, dep, rel) in field['edges']:
        heads[dep] = head
        deprels[dep] = rel
    return heads, deprels

def entity_finder(tokens, entity):
    start_positions = []
    for i in range(len(tokens)):
        if tokens[i] == entity[0] and tokens[i:i+len(entity)] == entity:
            start_positions.append(i)
    return start_positions


def format_output(hyponym, hypernym_candidate, sentence):
    # get head and dependency labels
    heads, deprels = heads_and_deprels(sentence)
    # get tokens used for finding entities
    tokens = get_field(sentence, 'raw')['tokens']
    # find all occurrences of subj
    subj_tokens = hyponym.split(' ')
    subj_starts = entity_finder(tokens, subj_tokens)
    subj_ends = [start + len(subj_tokens) - 1 for start in subj_starts]
    # find all occurrences of obj
    obj_tokens = hypernym_candidate.split(' ')
    obj_starts = entity_finder(tokens, obj_tokens)
    obj_ends = [start + len(obj_tokens) - 1 for start in obj_starts]
    # generate results
    for (subj_start, subj_end) in zip(subj_starts, subj_ends):
        for (obj_start, obj_end) in zip(obj_starts, obj_ends):
            yield {
                'id': uuid.uuid1().hex,
                'docid': sentence['docid'],  
                'relation': 'no_relation',
                'token': get_field(sentence, 'raw')['tokens'],
                'subj_start': subj_start,
                'subj_end': subj_end,
                'obj_start': obj_start,
                'obj_end': obj_end,
                'subj_type': 'ENTITY',
                'obj_type': 'ENTITY',
                'stanford_pos': get_field(sentence, 'tag')['tokens'],
                'stanford_ner': get_field(sentence, 'entity')['tokens'],
                'stanford_head': heads,
                'stanford_deprel': deprels,
            }


def main(datadir, outputname):
    results = []
    for hyponym in os.listdir(datadir):
        #print(hyponym)
        hyponym_path = os.path.join(datadir,hyponym)
        if os.path.isdir(hyponym_path):
            for filename in glob.glob(os.path.join(hyponym_path, "*.tsv")):
                 print(filename)
                 hypernym_candidate = os.path.splitext(os.path.basename(filename))[0]
                 for sentence in read_sentences_from_tsv(filename):
                     for datum in format_output(hyponym, hypernym_candidate, sentence):
                          results.append(datum)
                 
    with open(outputname, 'w') as f:
        print(json.dumps(results), file=f)
        



if __name__ == '__main__':
    # python convert_data.py /path/to/data results.filename.json
    datadir = sys.argv[1]
    outputname = sys.argv[2]
    #print("reads the data directory")
    #print(datadir)
    main(datadir, outputname)
