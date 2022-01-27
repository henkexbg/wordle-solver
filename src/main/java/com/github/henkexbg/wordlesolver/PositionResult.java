package com.github.henkexbg.wordlesolver;
/**
 * 
 * @author Henrik
 *
 */
public class PositionResult {

	PositionResultState positionResultState;

	Character c;

	public PositionResult(PositionResultState positionResultState, Character c) {
		this.positionResultState = positionResultState;
		this.c = c;
	}

	@Override
	public String toString() {
		return "PositionGuess [positionResultState=" + positionResultState + ", c=" + c + "]";
	}

}