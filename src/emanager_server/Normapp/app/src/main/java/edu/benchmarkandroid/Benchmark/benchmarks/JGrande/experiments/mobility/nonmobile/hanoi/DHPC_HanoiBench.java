package edu.benchmarkandroid.Benchmark.benchmarks.JGrande.experiments.mobility.nonmobile.hanoi;

import edu.benchmarkandroid.service.ProgressUpdater;

public class DHPC_HanoiBench implements Runnable {

    private ProgressUpdater progressUpdater;

    private long result;

    public DHPC_HanoiBench(ProgressUpdater progressUpdater) {
        this.progressUpdater = progressUpdater;
    }

    public void JGFinitialise() {
    }

    public void JGFkernel() {
        // result = hanoi(datasizes[size]);
        char FromPole = 'A', ToPole = 'B', WithPole = 'C';
        hanoi(28, FromPole, ToPole, WithPole);
    }

    public void run() {
        JGFrun();
    }

    public void JGFvalidate() {
    }

    public void JGFtidyup() {
    }

    public void JGFrun() {

        JGFinitialise();
        JGFkernel();
        JGFvalidate();
        JGFtidyup();

    }

    int moves = 0; // number of moves so far

    void hanoi(int height, char fromPole, char toPole, char withPole) {
        int[] HeightStack = new int[height];
        char[] fromStack = new char[height], toStack = new char[height], withStack = new char[height];
        int[] ReturnAddr = new int[height];
        int SP = -1; // Stack Pointer, initially empty stack
        int SUB = height - 1; // Stack Upper Bound
        int flag = 1;
        boolean done = false;
        char tmp;

        do {
            switch (flag) {
                case 1:
                    while (height > 0) {
                        SP++;
                        if (SP > SUB) {
                            progressUpdater.updateLog("");
                            progressUpdater.updateLog("*** Error: Stack Overflow");
                            return;
                        }
                        HeightStack[SP] = height;
                        fromStack[SP] = fromPole;
                        toStack[SP] = toPole;
                        withStack[SP] = withPole;
                        ReturnAddr[SP] = 2;
                        height--;
                        tmp = toPole; // swap
                        toPole = withPole; // values
                        withPole = tmp; //
                    }
                    flag = 3;
                    break;
                case 2:
                    moveDisk(fromPole, toPole);
                    SP++;
                    if (SP > SUB) {
                        progressUpdater.updateLog("");
                        progressUpdater.updateLog("*** Error: Stack Overflow");
                        return;
                    }
                    HeightStack[SP] = height;
                    fromStack[SP] = fromPole;
                    toStack[SP] = toPole;
                    withStack[SP] = withPole;
                    ReturnAddr[SP] = 3;
                    height--;
                    tmp = fromPole; // swap
                    fromPole = withPole; // values
                    withPole = tmp; //
                    flag = 1;
                    break;
                case 3:
                    if (SP >= 0) // stack not empty
                    {
                        while (SP >= 0 && flag == 3) {
                            height = HeightStack[SP];
                            fromPole = fromStack[SP];
                            toPole = toStack[SP];
                            withPole = withStack[SP];
                            flag = ReturnAddr[SP];
                            SP--;
                        }
                    } else {
                        done = !done;
                    }
                    break;
            }
        } while (!done);
    }

    void moveDisk(char fromPole, char toPole) {
        moves++;
    }

}
