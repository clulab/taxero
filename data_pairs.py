#!/usr/bin/env python

import os
from tqdm import trange

def read_hypernym_candidates(filename):
    candidates = []
    with open(filename) as f:
        for line in f:
            candidates.append(line.strip())
    return candidates

def read_hyponyms(filename):
    hyponyms = []
    with open(filename) as f:
        for line in f:
            hyponym, type = line.split('\t')
            hyponyms.append(hyponym)
    return hyponyms

def read_hypernyms(filename):
    hypernyms = []
    with open(filename) as f:
        for line in f:
            hs = [h.strip() for h in line.split('\t')]
            hypernyms.append(set(hs))
    return hypernyms

def main():
    candidates = read_hypernym_candidates('SemEval2018-Task9/vocabulary/1A.english.vocabulary.txt')
    hyponyms = read_hyponyms('SemEval2018-Task9/training/data/1A.english.training.data.txt')
    hypernyms = read_hypernyms('SemEval2018-Task9/training/gold/1A.english.training.gold.txt')
    outdir = 'allFiles'
    if not os.path.exists(outdir):
        os.makedirs(outdir)
    # with open('pairs.tsv', 'w') as f:
    for i in trange(len(hyponyms)):
        hypo = hyponyms[i]
        # one set of files per hyponym
        pos_file = os.path.join(outdir, f'{hypo}.pos.tsv')
        neg_file = os.path.join(outdir, f'{hypo}.neg.tsv')
        with open(pos_file, 'w') as pos, open(neg_file, 'w') as neg:
            hypers = hypernyms[i]
            for candidate in candidates:
                is_hyper = int(candidate in hypers)
                if is_hyper == 1:
                    print(f'{hypo}\t{candidate}\t{is_hyper}', file=pos)
                else:
                    print(f'{hypo}\t{candidate}\t{is_hyper}', file=neg)

if __name__ == '__main__':
    main()
